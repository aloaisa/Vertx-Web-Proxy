package proxy

void makeProxyRequest(def request, def client) {

    checkAllowRequest(request)
    redirectRequest(request, client)
}

void redirectRequest(def request, def client) {

    def clientRequest = client.request(request.method, request.uri) { clientResponse ->
        controlClientResponse(clientResponse, request)
    }

    clientRequest.chunked = true
    clientRequest.headers.set(request.headers)

    request.dataHandler { data ->
        clientRequest << data
    }

    request.endHandler { clientRequest.end() }
}

void controlClientResponse(def clientResponse, def request) {
    logger "Proxying response: ${clientResponse.statusCode}"

    request.response.chunked = true
    request.response.statusCode = clientResponse.statusCode
    request.response.headers.set(clientResponse.headers)

    clientResponse.dataHandler { data ->
        request.response << data
    }

    clientResponse.endHandler { request.response.end() }
}

void checkAllowRequest(def request) {

    if (areSecurityPoliciesPassed(request)) {
        logger "STOP request. Prohibited Uri, Method or Content-Type: ${request.method} - ${request.headers.get('Content-Type')} - ${request.uri}"
        throw new Exception()
    }

    logger "Proxying request: http://${config.finalHost}:${config.finalPort}${request.uri}"
}

boolean areSecurityPoliciesPassed(def request) {
    (request.uri in config.prohibitedUriList) ||
    (request.method in config.prohibitedMethodsList) ||
    (request.headers.get('Content-Type') != 'application/json')
}

void responseError(def request) {
    request.response.statusCode = 403
    request.response.end()
}

void logger(String text) {
    println "[${new Date()}] ${text}"
}

/** MAIN **/

def propertiesFilePath = "conf/proxy.properties"
config = new ConfigSlurper().parse(new File(propertiesFilePath).toURL())

def client = vertx.createHttpClient(port: config.finalPort, host: config.finalHost)

def server = vertx.createHttpServer().requestHandler { request ->
    
    try {
        makeProxyRequest(request, client)
    } catch (error) {
        responseError(request)
    }
    
}.listen(8080)
