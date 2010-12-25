/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jayway.restassured.internal

import com.jayway.restassured.authentication.AuthenticationScheme
import com.jayway.restassured.authentication.NoAuthScheme
import com.jayway.restassured.specification.AuthenticationSpecification
import com.jayway.restassured.specification.RequestSpecification
import com.jayway.restassured.specification.ResponseSpecification
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import static com.jayway.restassured.assertion.AssertParameter.notNull
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

class RequestSpecificationImpl implements RequestSpecification {

  private String baseUri
  private String path  = ""
  private int port
  private Map<String, String> requestParameters = [:]
  private AuthenticationScheme authenticationScheme = new NoAuthScheme()
  private ResponseSpecification responseSpecification;
  private ContentType requestContentType;
  private Map<String, String> requestHeaders = [:]
  private Map<String, String> cookies = [:]
  private Object requestBody;

  public RequestSpecificationImpl (String baseURI, int requestPort) {
    notNull(baseURI, "baseURI");
    this.baseUri = baseURI
    port(requestPort)
  }

  def RequestSpecification when() {
    return this;
  }

  def RequestSpecification given() {
    return this;
  }

  def RequestSpecification that() {
    return this;
  }

  def ResponseSpecification response() {
    return responseSpecification;
  }

  def void get(String path) {
    notNull path, "path"
    sendRequest(path, GET, responseSpecification.assertionClosure);
  }

  def void post(String path) {
    notNull path, "path"
    sendRequest(path, POST, responseSpecification.assertionClosure);
  }

  def void put(String path) {
    notNull path, "path"
    sendRequest(path, PUT, responseSpecification.assertionClosure);
  }

  def void delete(String path) {
    notNull path, "path"
    sendRequest(path, DELETE, responseSpecification.assertionClosure);
  }

  def void head(String path) {
    notNull path, "path"
    sendRequest(path, HEAD, responseSpecification.assertionClosure);
  }

  def RequestSpecification parameters(String parameterName, String... parameterNameValuePairs) {
    notNull parameterName, "parameterName"
    return parameters(MapCreator.createMapFromStrings(parameterName, parameterNameValuePairs))
  }

  def RequestSpecification parameters(Map<String, String> parametersMap) {
    notNull parametersMap, "parametersMap"
    this.requestParameters += Collections.unmodifiableMap(parametersMap)
    return this
  }

  def RequestSpecification params(String parameterName, String... parameterNameValuePairs) {
    return parameters(parameterName, parameterNameValuePairs)
  }

  def RequestSpecification params(Map<String, String> parametersMap) {
    return parameters(parametersMap)
  }

  def RequestSpecification param(String parameterName, String parameterValue) {
    return parameter(parameterName, parameterValue)
  }

  def RequestSpecification parameter(String parameterName, String parameterValue) {
    notNull parameterName, "parameterName"
    notNull parameterValue, "parameterValue"
    this.requestParameters.put(parameterName, parameterValue);
    return this
  }

  def RequestSpecification and() {
    return this;
  }

  def RequestSpecification request() {
    return this;
  }

  def RequestSpecification with() {
    return this;
  }

  def ResponseSpecification then() {
    return responseSpecification;
  }

  def ResponseSpecification expect() {
    return responseSpecification;
  }

  def AuthenticationSpecification auth() {
    return new AuthenticationSpecificationImpl(this);
  }

  def AuthenticationSpecification authentication() {
    return auth();
  }

  def RequestSpecification port(int port) {
    if(port < 1) {
      throw new IllegalArgumentException("Port must be greater than 0")
    }
    this.port = port
    return this
  }

  def RequestSpecification body(String body) {
    notNull body, "body"
    this.requestBody = body;
    return this;
  }

  RequestSpecification content(String content) {
    notNull content, "content"
    return content(content);
  }

  def RequestSpecification body(byte[] body) {
    notNull body, "body"
    this.requestBody = body;
    return this;
  }

  RequestSpecification content(byte[] content) {
    notNull content, "content"
    return content(content);
  }

  RequestSpecification contentType(ContentType contentType) {
    notNull contentType, "contentType"
    this.requestContentType = contentType
    return  this
  }

  RequestSpecification headers(Map<String, String> headers) {
    notNull headers, "headers"
    this.requestHeaders += headers;
    return this;
  }

  RequestSpecification header(String headerName, String headerValue) {
    notNull headerName, "headerName"
    notNull headerValue, "headerValue"
    requestHeaders.put(headerName, headerValue);
    return this
  }

  RequestSpecification headers(String headerName, String ... headerNameValueParis) {
    return headers(MapCreator.createMapFromStrings(headerName, headerNameValueParis))
  }

  RequestSpecification cookies(String cookieName, String... cookieNameValuePairs) {
    return cookies(MapCreator.createMapFromStrings(cookieName, cookieNameValuePairs))
  }

  RequestSpecification cookies(Map<String, String> cookies) {
    notNull cookies, "cookies"
    this.cookies += cookies;
    return this;
  }

  RequestSpecification cookie(String key, String value) {
    notNull key, "key"
    notNull value, "value"
    cookies.put(key, value)
    return this
  }

  private def sendRequest(path, method, assertionClosure) {
    def http = new HTTPBuilder(getTargetURI(path))
    http.getHeaders() << requestHeaders
    if(!cookies.isEmpty()) {
      http.getHeaders() << [Cookie : cookies.collect{it.key+"="+it.value}.join("; ")]
    }
    def responseContentType =  assertionClosure.getResponseContentType()
    authenticationScheme.authenticate(http)
    if(POST.equals(method)) {
      try {
        if(!requestParameters.isEmpty() && requestBody != null) {
          throw new IllegalStateException("You can either send parameters OR body content in the POST, not both!");
        }
        def bodyContent = requestParameters.isEmpty() ? requestBody : requestParameters
        http.post( path: path, body: bodyContent,
                requestContentType: defineRequestContentType(POST),
                contentType: responseContentType) { response, content ->
          if(assertionClosure != null) {
            assertionClosure.call (response, content)
          }
        }
      } catch(HttpResponseException e) {
        if(assertionClosure != null) {
          assertionClosure.call(e.getResponse())
        } else {
          throw e;
        }
      }
    } else {
      http.request(method, responseContentType) {
        uri.path = path

        if(requestBody != null) {
          body = requestBody
        }

        if(requestParameters != null) {
          uri.query = requestParameters
        }
        requestContentType: defineRequestContentType(method)

        Closure closure = assertionClosure.getClosure()
        // response handler for a success response code:
        response.success = closure

        // handler for any failure status code:
        response.failure = closure
      }
    }
  }

  private def defineRequestContentType(Method method) {
    if (requestContentType == null) {
      if (requestBody == null) {
        requestContentType = method == POST ? URLENC : ANY
      } else if (requestBody instanceof byte[]) {
        if(method != POST) {
          throw new IllegalStateException("$method doesn't support binary request data.");
        }
        requestContentType = BINARY
      } else {
        requestContentType = TEXT
      }
    }
    requestContentType
  }

  private String getTargetURI(String path) {
    if(port <= 0) {
      throw new IllegalArgumentException("Port must be greater than 0")
    }
    def uri
    def hasScheme = path.contains("://")
    if(hasScheme) {
      uri = path;
    } else {
      uri = "$baseUri:$port"
    }
    return uri
  }

  def void setResponseSpecification(ResponseSpecification responseSpecification) {
    this.responseSpecification = responseSpecification
  }
}