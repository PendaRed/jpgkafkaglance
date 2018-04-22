function showError(errorMessage) {
    document.getElementById("failureReason").innerHTML=errorMessage
}

function getCookie(cname) {
    var name = cname + "=";
    var decodedCookie = decodeURIComponent(document.cookie);
    var ca = decodedCookie.split(';');
    for (var i = 0; i<ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0)==' ') {
            c = c.substring(1);
        }
        if (c.indexOf(name)==0) {
            return c.substring(name.length, c.length);
        }
    }
    return "";
}

function displayMessageFromCode(errCode) {
    if (errCode.indexOf("badUserPass=b") >= 0) {
        console.log("Bad user name or password");
        showError("<div align='center'><b>Bad username or password</b></br></div>");
    }
    if (errCode.indexOf("badUserPass=i") >= 0) {
        console.log("Bad IPAddress");
        showError("<div align='center'><b>Bad client address, please login again</b></br></div>");
    }
    if (errCode.indexOf("badUserPass=t") >= 0) {
        console.log("Bad user name or password");
        showError("<div align='center'><b>Session timeout, please login again</b></br></div>");
    }
    if (errCode.indexOf("badUserPass=u") >= 0) {
        console.log("Bad user name or password");
        showError("<div align='center'><b>Unkown session, please login again</b></br></div>");
    }
}
function displayMessage() {
    // cookie is a string 'like' artifact, so get it as iff it is one
    var cookieVals = document.cookie;
    displayMessageFromCode(cookieVals);
}

function getSillyCyphered() {
    var username = document.getElementById("unFld").value;
    var password = document.getElementById("pwFld").value;

    var authEncoded = btoa(username+":"+password);
    var m = "";
    var cypher1="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/=";
    var cypher2="ctPMRIH3l0QKZTEW9Xxbgah81emfOG5di2jkSV4z6nsv/oqwupyrFUL+DBN7JAYC=";
    for (i=0; i<authEncoded.length; i++) {
        var c = authEncoded.charAt(i);
        m = m+cypher2.charAt(cypher1.indexOf(c));
    }
    return m;
}
//function loginNowDoesntWork() {
//    var form = document.createElement("authform");
//    form.setAttribute("method","POST");
//    form.setAttribute("action","auth.html");
//    var hiddenField = document.createElement("input");
//    hiddenField.setAttribute("type","hidden");
//    hiddenField.setAttribute("name","auth");
//    hiddenField.setAttribute("value",getSillyCyphered());
//    form.appendChild(hiddenField);
//    document.body.appendChild(form);
//    form.submit();
//    document.body.removeChild(form);
//}

function loginNow() {
    showError("");
    var xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function() {
      if (this.readyState == 4) {
        if (this.status == 200) {
            var response = JSON.parse(xhttp.responseText);
            console.log(response);
            if (response.isOk == "OK") {
                // server side class is GlanceLoginInfo
                var cookieVal = response.cookieVal
                document.cookie = "kgSessionId="+cookieVal;
                window.location.replace('kafkaglance.html');
            } else {
               showError("<div align='center'><b>Bad user name or password</b></br></div>");
            }

        } else {
           showError("<div align='center'><b>Inernal error</b></br></div>");
        }
      }
    };
    var formData = new FormData();
    formData.append("auth", getSillyCyphered());
    xhttp.withCredentials=true;
    xhttp.open("POST", "auth.html", true);
    xhttp.send(formData);
    return false;
}

//function onloadSetupEvents() {
//// Access the form element...
//  var form = document.getElementById("loginfrm");
//
//  // ...and take over its submit event.
//  form.addEventListener("submit", function (event) {
//    event.preventDefault();
//
//    loginNowDoesntWork();
//  });
//}
//
//onloadSetupEvents();

// Do this last as dom now setup
displayMessage();
