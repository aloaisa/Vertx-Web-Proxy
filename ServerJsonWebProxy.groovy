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

    logger "Proxying request: ${request.method} - ${request.headers.get('Content-Type')} - http://${config.finalHost}:${config.finalPort}${request.uri}"
    
    checkAllowMethod(request.method)
    checkAllowUri(request.uri)
    checkAllowContentType(request.headers.get('Content-Type'))
}

void checkAllowMethod(def method) {

    boolean methodAllow = (method in config.allowMethodList)
    if (!methodAllow) {
        logger "STOP request. Method not allow: ${method}"
        throw new Exception()
    }
}

void checkAllowUri(def uri) {

    def find = config.allowBeginningUriList.find { uriAllow -> uri.startsWith uriAllow }
    if (!find) {
        logger "STOP request. Uri not allow: ${uri}"
        throw new Exception()
    }
}

void checkAllowContentType(def contentType) {
    if (contentType != 'application/json') {
        logger "STOP request. Content-Type not allow: ${contentType}"
        throw new Exception()
    }
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
