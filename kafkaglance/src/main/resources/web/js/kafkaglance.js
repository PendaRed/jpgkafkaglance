
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

var cookie = getCookie("kgSessionId")
console.log("onload kgSessionId="+cookie)
var requestInProgress=true;
var requestStartTime = new Date();
var timeoutId = 0;

function displayMessage(msg) {
    document.getElementById("displayMessage").innerHTML = "<div style='text-align: center;'>"+msg+"</div>";
}

function displayTopicData(envName, topicDataArray) {
    var lastDateTime = ""
    var txt = "<table id='topics'><tr><th>Topic</th><th>GroupId</th><th class='num'>Consumers</th><th class='num'>Lag</th><th class='num'>Committed</th><th class='num'>End Offset</th></tr>"
    for (x in topicDataArray) {
        var lag = topicDataArray[x].endOffset - topicDataArray[x].commitedOffset;
        txt += "<tr><td>" + topicDataArray[x].topicName + "</td>";
        txt += "<td>" + topicDataArray[x].consumerName + "</td>";
        if (topicDataArray[x].consumerName.length>0) {
            txt += "<td class='num'>" + topicDataArray[x].numConsumers + "</td>";
            txt += "<td class='num'>" + lag + "</td>";
            txt += "<td class='num'>" + topicDataArray[x].commitedOffset + "</td>";
        } else {
            txt += "<td class='num'></td>";
            txt += "<td class='num'></td>";
            txt += "<td class='num'></td>";
        }
        txt += "<td class='num'>" + topicDataArray[x].endOffset + "</td>";
        txt += "</tr>";
        lastDateTime = topicDataArray[x].dateStr
    }
    txt += "</table>"
    displayMessage(envName+" : Last refreshed: "+lastDateTime)
    document.getElementById("topicData").innerHTML = txt;
    document.title = envName
}

function displayHomeData(responseMap) {
    var txt = "<div class='infoDiv'><img src='images/kglance.png'></br>";
    txt += "<table id='topics'>"
    for (var key in responseMap) {
        if (responseMap.hasOwnProperty(key)) {
          var value = responseMap[key];
          txt += "<tr><td>"+key+"</td><td class='break'>"+value+"</td></tr>";
        }
    }
    txt += "</table>";
    txt += "</div>";
    document.getElementById("topicData").innerHTML = txt;
    document.getElementById("displayMessage").innerHTML = "";
}

function displayData(response) {
    requestInProgress=false
    if (timeoutId >0) {
        clearInterval(timeoutId);
        timeoutId=0;
    }
    if (response.name=="topics") {
        if (response.data.length>0) {
            displayTopicData(response.envName, response.data)
        } else {
            document.getElementById("topicData").innerHTML = "";
            displayMessage(response.forceLogin)

        }
    } else if (response.name=="info") {
       displayHomeData(response.data)
    }
}

function showRequestProgress() {
    if (!requestInProgress) return

    var now = new Date();
    var elapsed = now.getTime()-requestStartTime.getTime();
    if (elapsed>3) {
        var secs = (elapsed/1000).toFixed(0);
        displayMessage(document.title+" : Waiting "+secs+" seconds for server.")
    }
}

function requestMadeNoReplyYet() {
    if (requestInProgress) {
        var now = new Date();
        var elapsed = now.getTime()-requestStartTime.getTime();
        if (elapsed<10000) {
            console.log("New request ignored as request in progress")
            return true;
        }
    }
    return false;
}

function refreshData(pagename) {
    if (requestMadeNoReplyYet()) return;

    requestStartTime = new Date();
    requestInProgress = true;
    if (timeoutId==0) {
        timeoutId = setInterval(showRequestProgress, 1000)
    }

    var cookie = getCookie("kgSessionId");
    console.log("kgSessionId="+cookie);
    var xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function() {
        if (this.readyState == 4) {
            if (this.status == 200) {
                // console.log(xhttp.responseText);
                // console.log("Try to decode as json")
                var response = JSON.parse(xhttp.responseText);
                // console.log(response)
                if (response.forceLogin=="Y") {
                    window.location.replace('login.html');
                } else {
                    displayData(response);
                }
            } else {
                document.getElementById("topicData").innerHTML = "";
                displayMessage("Data load failed.  Is the server down?");
            }
        }
    };
    xhttp.withCredentials=true;
    xhttp.open("GET", pagename, true);
    xhttp.setRequestHeader("Content-type", "application/json");
    console.log("New request to page "+pagename)

    xhttp.send();
}

function refreshHomeData() {
    refreshData("kghome.html");
}

function refreshTopicData() {
    refreshData("kgdata.html");
}

function onloadSetUpLinks() {
    document.getElementById("homeLink").onclick=refreshHomeData;
    document.getElementById("refreshLink").onclick=refreshTopicData;
}


onloadSetUpLinks();

requestInProgress=false;
refreshTopicData();

