package com.soapuiToPostman.soapuiToPostman;

import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.gson.*;

@RestController
@RequestMapping("/api/convert")
class Converter {

    @PostMapping(value = "/soapui-to-postman", produces = MediaType.APPLICATION_JSON_VALUE)
    public String convertSoapUIToPostman(@RequestParam("file") MultipartFile soapUIFile) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(soapUIFile.getInputStream());

        JsonObject collection = new JsonObject();
        collection.add("info", createInfo());

        JsonArray items = Stream.of(doc.getElementsByTagName("con:testStep"))
            .map(Element.class::cast)
            .filter(step -> "restrequest".equals(step.getAttribute("type")))
            .map(this::createRequestItem)
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);

        collection.add("item", items);

        return new GsonBuilder().setPrettyPrinting().create().toJson(collection);
    }

    private JsonObject createInfo() {
        JsonObject info = new JsonObject();
        info.addProperty("name", "Converted SoapUI Collection");
        info.addProperty("_postman_id", java.util.UUID.randomUUID().toString());
        info.addProperty("description", "Converted from SoapUI XML");
        info.addProperty("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        return info;
    }

    private JsonObject createRequestItem(Element testStep) {
        Element config = (Element) testStep.getElementsByTagName("con:config").item(0);
        String method = config.getElementsByTagName("con:method").item(0).getTextContent();
        String endpoint = config.getElementsByTagName("con:endpoint").item(0).getTextContent();

        JsonObject request = new JsonObject();
        request.addProperty("method", method);

        JsonObject url = new JsonObject();
        url.addProperty("raw", endpoint);
        request.add("url", url);

        JsonArray headers = Stream.of(config.getElementsByTagName("con:property"))
            .map(Element.class::cast)
            .filter(p -> p.getAttribute("name").startsWith("header_"))
            .map(p -> {
                JsonObject header = new JsonObject();
                header.addProperty("key", p.getAttribute("name").substring(7));
                header.addProperty("value", p.getTextContent());
                return header;
            })
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
        request.add("header", headers);

        Node requestBodyNode = config.getElementsByTagName("con:request").item(0);
        if (requestBodyNode != null) {
            JsonObject body = new JsonObject();
            body.addProperty("mode", "raw");
            body.addProperty("raw", requestBodyNode.getTextContent());
            request.add("body", body);
        }

        JsonObject item = new JsonObject();
        item.addProperty("name", testStep.getAttribute("name"));
        item.add("request", request);

        return item;
    }
}
