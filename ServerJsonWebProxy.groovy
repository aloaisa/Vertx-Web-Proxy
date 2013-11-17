package proxy

// Example URL => http://api.openweathermap.org/data/2.5/weather?q=London,uk
def propertiesFilePath = "conf/proxy.properties"
config = new ConfigSlurper().parse(new File(propertiesFilePath).toURL())

def client = vertx.createHttpClient(port: config.finalPort, host: config.finalHost)

def server = vertx.createHttpServer().requestHandler { request ->
    
    if (isAllowRequest(request)) {
        redirectRequest(request, client)
    } else {
        responseError(request)
    }

}.listen(8080)

def redirectRequest(def request, def client) {

    def clientRequest = client.request(request.method, request.uri) { clientResponse ->
        controlClientResponse(clientResponse, request)
    }

    clientRequest.chunked = true
    clientRequest.headers.set(request.headers)

    request.dataHandler { data ->
        clientRequest << data
    }

    request.endHandler{ clientRequest.end() }
}

def controlClientResponse(def clientResponse, def request) {
    logger "Proxying response: ${clientResponse.statusCode}"

    request.response.chunked = true
    request.response.statusCode = clientResponse.statusCode
    request.response.headers.set(clientResponse.headers)

    clientResponse.dataHandler { data ->
        request.response << data
    }

    clientResponse.endHandler { request.response.end() }
}


def isAllowRequest(def request) {

    if ((request.uri in config.prohibitedUriList) || (request.method in config.prohibitedMethodsList)) {
        logger "STOP request, prohibited Uri or Method: ${request.method} - ${request.uri}"
        return false
    }

    logger "Proxying request: http://${config.finalHost}:${config.finalPort}${request.uri}"
    return true
}

def responseError(def request) {

    request.response.statusCode = 403
    request.response.end()
}

def logger(String text) {
    println "[${new Date()}] ${text}"
}
