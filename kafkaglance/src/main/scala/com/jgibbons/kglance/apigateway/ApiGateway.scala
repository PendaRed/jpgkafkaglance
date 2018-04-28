package com.jgibbons.kglance.apigateway

import java.io.{File, PrintWriter, StringWriter}

import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import akka.http.javadsl.model.headers.CacheControl
import akka.http.javadsl.server.Route
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCookie, HttpCookiePair, RawHeader}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.jgibbons.kglance.KafkaGlanceGuardianActor.GuardianInitialiseInMsg
import com.jgibbons.kglance.KafkaGlanceGuardianActor
import com.jgibbons.kglance.config.KafkaGlanceConfig
import com.typesafe.config.ConfigFactory
import akka.pattern.{AskTimeoutException, ask}
import akka.util.{ByteString, Timeout}
import com.jgibbons.kglance.kafkaadmin._
import com.jgibbons.kglance.kafkaadmin.KafkaInfoActor.{GetKafkaInfoInMsg, GetLatestStatsInMsg, KafkaInfoOutMsg, LatestTopicInfoOutMsg}
import com.jgibbons.kglance.usersessions.UserSessionCacheActor._

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Created by Jonathan during 2018.
  */
object ApiGateway  {
  val COOKIE_SHOW_REASON ="badUserPass"
  val FAILREASON_BAD_LOGIN = "b"
  val FAILREASON_SESSION_TIMEOUT ="t"
  val FAILREASON_BAD_IPADDR ="i"
  val FAILREASON_UNKNOWN_SESSION ="u"

  val COOKIE_SESSION_ID = "kgSessionId"
}

/**
  * GlanceJsonSupport pulls in the implicit for spray.json
  */
class ApiGateway extends GlanceJsonSupport {
  implicit val system = ActorSystem("KafkaGlance")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  // Implicit timeout for the ask
  implicit val timeout = Timeout(20 seconds)

  val myGuardian = system.actorOf(KafkaGlanceGuardianActor.props(), name = "KafkaGlanceGuardianActor")
  val usefulActors: UsefulActors = boostrapUsefulActorsFromTheGuardian

  //***************************************
  // Needed for logging outside of an actor.
  implicit val myLogSourceType: LogSource[ApiGateway] = new LogSource[ApiGateway] {
    def genString(a: ApiGateway) = a.getClass.getName
  }
  val log = Logging(system, this)
  val config = KafkaGlanceConfig(ConfigFactory.load())
  val envNameForWebpage = config.envName

  config.dumpConfig("")

  val adminUsername = config.username
  val adminPassword = config.password

  /**
    * All rejections should result in a redirect to the login page with a description of why
    */
  implicit val myRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case MissingCookieRejection(cookieName) =>
        log.info("rejected missing cookie")
        // according to wiki, using see other is the correct mechanism and is pary of the
        // Post/Redirect/Get pattern
        redirect(Uri("http://" + config.hostname + ":" + config.portNum + "/login.html"), StatusCodes.SeeOther)
      case rej@_ =>
        log.info("rejected due to [" + rej + "]")
        complete(HttpResponse(entity = "rejected due to [" + rej + "]"))
    }
    .result()

  /**
    * All exception should show the user what the problem is
    */
  implicit def myExceptionHandler =
    ExceptionHandler {
      case e: AskTimeoutException =>
        extractUri { uri =>
          log.error(e, s"Internal ask timeout error serving URL $uri")
          complete(GlanceNamedList("topics", envNameForWebpage + " Internal timeout, perhaps you have too many topics for Glance to handle?", envNameForWebpage, List.empty[GlanceTopicInfo]))
        }
      case e: Exception =>
        extractUri { uri =>
          val sw = new StringWriter()
          e.printStackTrace(new PrintWriter(sw))
          val stackTrace = sw.toString
          log.error(e, s"Internal Server Error serving URL $uri")

          val ent = HttpEntity(ContentTypes.`text/html(UTF-8)`,
            s"""
           |<http><head><title>Internal server error</title></head><body>
           |<h2>Internal server error</h2>
           |<p>Sorry, we are unable to deal with you at this time</p>
           |<p>Exceotion cause request for $uri</p>
           |<pre>$stackTrace</pre>
           |</body></http>
           | """.stripMargin)
          complete(HttpResponse(InternalServerError, entity = ent))
        }
    }

  val returnLoginPage = {
    // optionally compresses the response with Gzip or Deflate
    // if the client accepts compressed responses
    encodeResponse {
      logAndGetFromFile("login.html")
    }
  }

  /**
    * This crappy idea is because I couldnt get akka.http to run the server within its
    * own actor - only tried briefly.  So instead I do this horror, still have a guardian,
    * but have it share the actors I need here.
    */
  def boostrapUsefulActorsFromTheGuardian : UsefulActors = {
    val future = myGuardian ? GuardianInitialiseInMsg
    val usefulActors = Await.result(future, 10 seconds).asInstanceOf[UsefulActors]
    usefulActors
  }

  def myUserPassAuthenticator(credentials:Credentials):Option[String] = {
    credentials match {
      case p@Credentials.Provided(id) if p.verify(adminPassword) && id == adminUsername =>
        Some(id)
      case p@Credentials.Provided(id) =>
        log.warning("Bad login attempt for user [$id]")
        None
      case _ =>
        log.warning("Bad login attempt")
        None
    }
  }

  private def getHostnameFromIp(remoteAddr:RemoteAddress) :String = remoteAddr.toOption match {
    case Some(inetAddress) => inetAddress.getHostName
    case None => "Unknown"
  }

  import scala.concurrent.duration._


  val route =
    toStrictEntity(10.seconds) {
      extractClientIP { remoteAddr:RemoteAddress =>
        val clientIpAddr = getHostnameFromIp(remoteAddr)
        get {
          extractRequest { request =>
            log.debug("GET "+ request.uri + "  from "+ clientIpAddr+ ", Headers:"+request.headers)
            val hostPort = request.uri.authority.toString
            val pageRequested = {
              val p = request.uri.path.toString()
              if (p.startsWith("/")) p.substring(1) else p
            }

            deleteCookie(ApiGateway.COOKIE_SHOW_REASON) {
              path("login.html") {
                returnLoginPage
              } ~  path("logout.html") {
                cookie(ApiGateway.COOKIE_SESSION_ID) { sessionIdCookie: HttpCookiePair =>
                  val sessionId = sessionIdCookie.value
                  deleteCookie(ApiGateway.COOKIE_SESSION_ID) {
                    log.debug(s"[$sessionId] User logout, redirecting to login.html")
                    redirect(Uri("http://" + hostPort + "/login.html"), StatusCodes.TemporaryRedirect)
                  }
                }
              } ~pathPrefix("favicon.ico") {
                logAndGetFile("images/", "favicon.png")
                //                complete((NotFound, s"Not found"))
              } ~pathPrefix("css") {
                extractUnmatchedPath { f => logAndGetFile("css/", f.toString()) }
              } ~ pathPrefix("js") {
                extractUnmatchedPath { f => logAndGetFile("js/", f.toString()) }
              } ~ pathPrefix("images") {
                extractUnmatchedPath { f => logAndGetFile("images/", f.toString()) }
              } ~  path("kafkaglance.html" | "kgdata.html" | "kghome.html") {
                validateCookieAndRouteToMainPage(clientIpAddr, hostPort, pageRequested)
              } ~ rejectEmptyResponse {
                log.debug("Redirecting to login.html, and removing any session cookie")
                deleteCookie(ApiGateway.COOKIE_SESSION_ID) {
                  redirect(Uri("http://" + hostPort + "/login.html"), StatusCodes.TemporaryRedirect)
                }
              }
            }
          }
        } ~ post {
          extractRequest {
            request =>
            val hostPort = request.uri.authority
            log.debug("POST "+ request.uri+ "  from "+ clientIpAddr+ ", headers:"+request.headers)
            path("auth.html") {
              formFields('auth) { (auth) => {
                val str = auth.toString
                val authBase64 = SillyCypher.decode(str)
                val decoded = new String(java.util.Base64.getDecoder().decode(authBase64))
                val userNamePassword: Array[String] = decoded.split(":")
                if ((userNamePassword.length == 2) &&
                  (userNamePassword(0) == adminUsername) && (userNamePassword(1) == adminPassword)) {
                  onSuccess(usefulActors.userSessionCache ? RegisterUserInMsg(adminUsername,clientIpAddr.toString())) {
                    case UserSessionIdOutMsg(cookieValue) =>
                      val loginInfo = GlanceLoginInfo("OK", cookieValue)
                      complete(loginInfo) // implicits in GlanceJsonSupport muster this up
                  }
                } else {
                  log.debug("Login invalid")
                  val loginInfo = GlanceLoginInfo("no", "foobar")
                  complete(loginInfo) // implicits in GlanceJsonSupport muster this up
                }
              }
              }
            }
          }
        }
      }
    }
  log.info(s"Starting Akka.Http, with host and port: ${config.hostname}:${config.portNum}")
  val bindingFuture = Http().bindAndHandle(route, config.hostname, config.portNum)

  bindingFuture.failed.foreach { ex =>
    log.error(ex,
      """Exception opening server socket, closing down.
        | Perhaps another instance is running already?
        | Or edit the config and change the port number so I am not blocked by another service.""".stripMargin)
    closeDown()
  }

  def logAndGetFile(dir:String, filename: String) = {
    logAndGetFromFile(dir+ {
      if (dir.endsWith("/") && filename.startsWith("/")) filename.substring(1)
      else filename
    })
  }
  def logAndGetFromFile(filename: String) = {
    log.info("Serve the page from " + filename)
    getFromResource("web/" + filename)
  }

  /**
    * A rest call to get the list of topics, consumers and so on
    */
  def completeWithKafkaData() = {
    onSuccess(usefulActors.kafkaUtilsActor ? GetLatestStatsInMsg) {
      case LatestTopicInfoOutMsg(payload: Option[List[GlanceTopicInfo]]) =>
        payload match {
          case Some(topicInfo) =>
            respondWithHeaders(RawHeader("Cache-Control", "no-cache")) {
              complete(GlanceNamedList("topics", "", envNameForWebpage, topicInfo)) // the GlanceJsonSupport trait has implicits t convert the data to JSon
            }
          case None => complete(GlanceNamedList("topics", envNameForWebpage + " No topic information available, perhaps Kafka is down?", envNameForWebpage, List.empty[GlanceTopicInfo]))
        }
    }
  }

  /**
    * Send back a map of kafka data, name, value for display on the home page
    */
  def completeWithKafkaHome() = {
    onSuccess(usefulActors.kafkaUtilsActor ? GetKafkaInfoInMsg) {
      case KafkaInfoOutMsg(payload:Option[Map[String, String]]) =>
        payload match {
          case Some(kafkaInfoMap) =>
            val withEnvName = Map("Environment"->envNameForWebpage)++kafkaInfoMap
            respondWithHeaders(RawHeader("Cache-Control", "no-cache")) {
              complete(GlanceNamedMap("info", "", withEnvName))
            }
          case None => complete(GlanceNamedMap("info", "No information available, perhaps Kafka is down?", Map.empty[String, String]))
        }
    }
  }

  def makePageRedirectToLogin() = {
    complete(GlanceNamedList("topics", "Y", "", List.empty[GlanceTopicInfo]))
  }

  def sendHttpRedirect(sessionId:String,hostPort:String, cookieVal:String, logReason:String) = {
    setCookie (HttpCookie (ApiGateway.COOKIE_SHOW_REASON, cookieVal) ) {
      log.debug (s"[$sessionId] $logReason, Redirecting to loginRetry.html")
      redirect (Uri ("http://" + hostPort + "/loginRetry.html"), StatusCodes.TemporaryRedirect)
    }
  }

  def validateCookieAndRouteToMainPage(clientIpAddr:String, hostPort:String, httpPath:String)= cookie(ApiGateway.COOKIE_SESSION_ID) {
    sessionIdCookie: HttpCookiePair =>
    val sessionId = sessionIdCookie.value
    onSuccess(usefulActors.userSessionCache ? ConfirmSessionIdInMsg(sessionId, clientIpAddr)) {
      case SessionIdCheckOutMsg(SessionValid) =>
        log.info(s"[$sessionId] SessionValid, allowing access to [$httpPath]")
        setCookie(HttpCookie(ApiGateway.COOKIE_SESSION_ID, sessionId)) {
          httpPath match {
            case "kgdata.html" => completeWithKafkaData()
            case "kghome.html" => completeWithKafkaHome()
            case "kafkaglance.html" => logAndGetFromFile(httpPath)
            case _ => logAndGetFromFile("kafkaglance.html")
          }
        }
      case SessionIdCheckOutMsg(SessionTimedOut) =>
        httpPath match {
          case "kgdata.html" | "kghome.html" =>makePageRedirectToLogin()
          case _ => sendHttpRedirect(sessionId, hostPort, ApiGateway.FAILREASON_SESSION_TIMEOUT, "Sessiontimeout")
        }
      case SessionIdCheckOutMsg(BadIpAddress) =>
        httpPath match {
          case "kgdata.html" | "kghome.html" =>makePageRedirectToLogin()
          case _ => sendHttpRedirect(sessionId, hostPort, ApiGateway.FAILREASON_BAD_IPADDR, "BadIpAddress")
        }
      case SessionIdCheckOutMsg(NoSessionStored) =>
        httpPath match {
          case "kgdata.html" | "kghome.html"  => makePageRedirectToLogin()
          case _ => sendHttpRedirect(sessionId, hostPort, ApiGateway.FAILREASON_UNKNOWN_SESSION, "NoSessionStored")
        }
    }
  }

  def closeDown() = system.terminate()
}