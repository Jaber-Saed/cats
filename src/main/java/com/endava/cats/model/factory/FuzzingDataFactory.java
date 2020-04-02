package com.endava.cats.model.factory;

import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible for creating {@link com.endava.cats.model.FuzzingData} objects based on the supplied OpenApi paths
 */
@Component
public class FuzzingDataFactory {

    private static final String APPLICATION_JSON = "application/json";
    private static final String SYNTH_SCHEMA_NAME = "catsGetSchema";

    private final CatsUtil catsUtil;

    @Autowired
    public FuzzingDataFactory(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    /**
     * Creates a list of FuzzingData objects that will be used to fuzz the provided PathItems. The reason there is more than one FuzzingData object is due
     * to cases when the contract uses OneOf or AnyOf composite objects which causes the payload to have more than one variation.
     *
     * @param path    the path from the contract
     * @param item    the PathItem containing the details about the interaction with the path
     * @param schemas
     * @return
     */
    public List<FuzzingData> fromPathItem(String path, PathItem item, Map<String, Schema> schemas) {
        List<FuzzingData> fuzzingDataList = new ArrayList<>();
        if (item.getPost() != null) {
            fuzzingDataList.addAll(this.getFuzzDataForPostOrPut(path, item, schemas, item.getPost(), HttpMethod.POST));
        }

        if (item.getPut() != null) {
            fuzzingDataList.addAll(this.getFuzzDataForPostOrPut(path, item, schemas, item.getPut(), HttpMethod.PUT));
        }

        if (item.getPatch() != null) {
            fuzzingDataList.addAll(this.getFuzzDataForPostOrPut(path, item, schemas, item.getPatch(), HttpMethod.PATCH));
        }

        if (item.getGet() != null) {
            fuzzingDataList.addAll(this.getFuzzDataForGet(path, item, schemas, item.getGet(), HttpMethod.GET));
        }

        return fuzzingDataList;
    }

    /**
     * A similar FuzzingData object will created for GET requests. The "payload" will be a JSON with all the query or path params.
     * In order to achieve this a "synth" object is created that will act as a root object holding all the query or path params as child schemas.
     *
     * @param path
     * @param item
     * @param schemas
     * @param operation
     * @param method
     * @return
     */
    private List<FuzzingData> getFuzzDataForGet(String path, PathItem item, Map<String, Schema> schemas, Operation operation, HttpMethod method) {
        Set<CatsHeader> headers = this.extractHeaders(operation);
        ObjectSchema syntheticSchema = this.createSyntheticSchemaForGet(operation.getParameters());

        schemas.put(SYNTH_SCHEMA_NAME + operation.getOperationId(), syntheticSchema);

        List<String> payloadSamples = this.getRequestPayloadsSamples(null, SYNTH_SCHEMA_NAME + operation.getOperationId(), schemas);
        Map<String, List<String>> responses = this.getResponsePayloads(operation, operation.getResponses().keySet(), schemas);

        return payloadSamples.stream().map(payload -> FuzzingData.builder().method(method).path(path).headers(headers).payload(payload)
                .responseCodes(operation.getResponses().keySet()).reqSchema(syntheticSchema).pathItem(item)
                .schemaMap(schemas).responses(responses)
                .requestPropertyTypes(PayloadGenerator.getRequestDataTypes())
                .catsUtil(catsUtil)
                .build()).collect(Collectors.toList());
    }

    /**
     * In order to increase reusability we will create a synthetic Schema object that will resemble the Schemas used by POST requests.
     * This will allow us to use the same code to fuzz the requests.
     * We will also do another "hack" so that required Parameters are also marked as required in their corresponding schema
     *
     * @param operationParameters
     * @return
     */
    private ObjectSchema createSyntheticSchemaForGet(List<Parameter> operationParameters) {
        ObjectSchema syntheticSchema = new ObjectSchema();
        for (Parameter parameter : operationParameters) {
            if ("path".equalsIgnoreCase(parameter.getIn()) || "query".equalsIgnoreCase(parameter.getIn())) {
                syntheticSchema.addProperties(parameter.getName(), parameter.getSchema());
                if (parameter.getRequired() != null && parameter.getRequired()) {
                    parameter.getSchema().setRequired(Collections.singletonList(CatsUtil.CATS_REQUIRED));
                }
            }
        }
        return syntheticSchema;
    }

    /**
     * The reason we get more than one {@code FuzzingData} objects is related to the usage of {@code anyOf, oneOf or allOf} elements inside the contract definition.
     * The method will compute all the possible combinations so that it covers all payload definitions.
     *
     * @param path
     * @param item
     * @param schemas
     * @param operation
     * @param method
     * @return
     */
    private List<FuzzingData> getFuzzDataForPostOrPut(String path, PathItem item, Map<String, Schema> schemas, Operation operation, HttpMethod method) {
        List<FuzzingData> fuzzingDataList = new ArrayList<>();
        MediaType mediaType = this.getMediaType(operation);

        if (mediaType == null) {
            return Collections.emptyList();
        }
        List<String> reqSchemaNames = this.getCurrentRequestSchemaName(mediaType);

        Map<String, List<String>> responses = this.getResponsePayloads(operation, operation.getResponses().keySet(), schemas);

        for (String reqSchemaName : reqSchemaNames) {
            List<String> payloadSamples = this.getRequestPayloadsSamples(mediaType, reqSchemaName, schemas);
            fuzzingDataList.addAll(payloadSamples.stream().map(payload ->
                    FuzzingData.builder().method(method).path(path).headers(this.extractHeaders(operation)).payload(payload)
                            .responseCodes(operation.getResponses().keySet()).reqSchema(schemas.get(reqSchemaName)).pathItem(item)
                            .schemaMap(schemas).responses(responses)
                            .requestPropertyTypes(PayloadGenerator.getRequestDataTypes())
                            .catsUtil(catsUtil)
                            .build()).collect(Collectors.toList()));
        }

        return fuzzingDataList;
    }

    /**
     * We get the definition name for each request type. This also includes cases when we have AnyOf or OneOf schemas
     *
     * @param mediaType
     * @return
     */
    private List<String> getCurrentRequestSchemaName(MediaType mediaType) {
        List<String> reqSchemas = new ArrayList<>();
        String currentRequestSchema = mediaType.getSchema().get$ref();

        if (currentRequestSchema == null && mediaType.getSchema() instanceof ArraySchema) {
            currentRequestSchema = ((ArraySchema) mediaType.getSchema()).getItems().get$ref();
        }
        if (currentRequestSchema != null) {
            reqSchemas.add(this.getSchemaName(currentRequestSchema));
        } else if (mediaType.getSchema() instanceof ComposedSchema) {
            ComposedSchema schema = (ComposedSchema) mediaType.getSchema();
            if (schema.getAnyOf() != null) {
                schema.getAnyOf().forEach(innerSchema -> reqSchemas.add(this.getSchemaName(innerSchema.get$ref())));
            }
            if (schema.getOneOf() != null) {
                schema.getOneOf().forEach(innerSchema -> reqSchemas.add(this.getSchemaName(innerSchema.get$ref())));
            }
        }

        return reqSchemas;
    }

    private String getSchemaName(String currentRequestSchema) {
        return currentRequestSchema.substring(Objects.requireNonNull(currentRequestSchema).lastIndexOf('/') + 1);
    }


    private MediaType getMediaType(Operation operation) {
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent().get(APPLICATION_JSON) != null) {
            return operation.getRequestBody().getContent().get(APPLICATION_JSON);
        } else if (operation.getRequestBody() != null) {
            return operation.getRequestBody().getContent().get("*/*");
        }
        return null;
    }

    private List<String> getRequestPayloadsSamples(MediaType mediaType, String reqSchemaName, Map<String, Schema> schemas) {
        PayloadGenerator generator = new PayloadGenerator(schemas);
        List<String> result = this.generateSample(reqSchemaName, generator);

        if (mediaType != null && mediaType.getSchema() instanceof ArraySchema) {
            /*when dealing with ArraySchemas we make sure we have 2 elements in the array*/
            result = result.stream().map(payload -> "[" + payload + "," + payload + "]").collect(Collectors.toList());
        }
        return result;
    }

    private List<String> generateSample(String reqSchemaName, PayloadGenerator generator) {
        List<Map<String, String>> examples = generator.generate(null, reqSchemaName);
        String payloadSample = examples.get(0).get("example");

        payloadSample = this.squashAllOfElements(payloadSample);
        return this.getPayloadCombinationsBasedOnOneOfAndAnyOf(payloadSample, generator.getDiscriminators());
    }

    /**
     * When we deal with AnyOf or OneOf data types, we need to create multiple payloads based on the number of sub-types defined within the contract. This method will return all these combinations
     * based on the keywords 'ANY_OF' and 'ONE_OF' generated by the PayloadGenerator
     *
     * @param initialPayload
     * @param discriminators
     * @return
     */
    private List<String> getPayloadCombinationsBasedOnOneOfAndAnyOf(String initialPayload, List<String> discriminators) {
        List<String> result = new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(initialPayload);

        if (jsonElement.isJsonObject()) {
            this.addNewCombination(result, jsonElement, discriminators);
        }
        if (result.isEmpty()) {
            result.add(initialPayload);
        }

        return result;
    }

    private void addNewCombination(List<String> result, JsonElement jsonElement, List<String> discriminators) {
        Map<String, JsonElement> anyOfOrOneOf = this.getAnyOrOneOffElements(jsonElement);
        anyOfOrOneOf.forEach((key, value) -> jsonElement.getAsJsonObject().remove(key));

        anyOfOrOneOf.forEach((key, value) ->
        {
            String newKey = key.substring(0, key.indexOf('#')).replace("ANY_OF", "").replace("ONE_OF", "");
            value.getAsJsonObject().entrySet().forEach(jsonElementEntry ->
                    {
                        String dataTypeKey = newKey + "#" + jsonElementEntry.getKey();
                        if (discriminators.contains(dataTypeKey)) {
                            value.getAsJsonObject().addProperty(jsonElementEntry.getKey(), key.substring(key.lastIndexOf('/') + 1));
                        }
                    }
            );

            jsonElement.getAsJsonObject().add(newKey, value);

            result.add(jsonElement.toString());
            jsonElement.getAsJsonObject().remove(key);
        });
    }

    private Map<String, JsonElement> getAnyOrOneOffElements(JsonElement jsonElement) {
        Map<String, JsonElement> anyOrOneOfs = new HashMap<>();

        for (Map.Entry<String, JsonElement> elementEntry : jsonElement.getAsJsonObject().entrySet()) {
            if (elementEntry.getKey().contains("ONE_OF") || elementEntry.getKey().contains("ANY_OF")) {
                anyOrOneOfs.put(elementEntry.getKey(), elementEntry.getValue());
            }
        }
        return anyOrOneOfs;
    }

    private String squashAllOfElements(String payloadSample) {
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(payloadSample);
        this.squashAllOf(jsonElement);

        payloadSample = jsonElement.toString();
        return payloadSample;
    }

    /**
     * When a sample payload is created by the PayloadGenerator, the ALL_OF elements are marked with the ALL_OF json key.
     * We now make sure that we combine all these elements under one root element.
     *
     * @param element
     */
    private void squashAllOf(JsonElement element) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : (element.getAsJsonObject().entrySet())) {
                squashAllOf(entry.getValue());
                if (entry.getKey().equalsIgnoreCase("ALL_OF")) {
                    element.getAsJsonObject().remove(entry.getKey());
                    for (Map.Entry<String, JsonElement> allOfEntry : entry.getValue().getAsJsonObject().entrySet()) {
                        element.getAsJsonObject().add(allOfEntry.getKey(), allOfEntry.getValue());
                    }
                }
            }
        } else if (element.isJsonArray()) {
            for (int i = 0; i < element.getAsJsonArray().size(); i++) {
                squashAllOf(element.getAsJsonArray().get(i));
            }
        }
    }

    /**
     * We need to get JSON structural samples for each response code documented into the contract
     *
     * @param operation
     * @param responseCodes
     * @param schemas
     * @return
     */
    private Map<String, List<String>> getResponsePayloads(Operation operation, Set<String> responseCodes, Map<String, Schema> schemas) {
        Map<String, List<String>> responses = new HashMap<>();
        PayloadGenerator generator = new PayloadGenerator(schemas);
        for (String responseCode : responseCodes) {
            String responseSchemaRef = this.extractResponseSchemaRef(operation, responseCode);
            if (responseSchemaRef != null) {
                String respSchemaName = this.getSchemaName(responseSchemaRef);
                List<String> samples = this.generateSample(respSchemaName, generator);

                responses.put(responseCode, samples);
            } else {
                responses.put(responseCode, Collections.emptyList());
            }
        }
        return responses;
    }

    private String extractResponseSchemaRef(Operation operation, String responseCode) {
        ApiResponse apiResponse = operation.getResponses().get(responseCode);
        if (StringUtils.isNotEmpty(apiResponse.get$ref())) {
            return apiResponse.get$ref();
        }
        if (apiResponse.getContent() != null && apiResponse.getContent().get(APPLICATION_JSON) != null) {
            return apiResponse.getContent().get(APPLICATION_JSON).getSchema().get$ref();
        }
        return null;
    }


    private Set<CatsHeader> extractHeaders(Operation operation) {
        Set<CatsHeader> headers = new HashSet<>();
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if (param.getIn().equalsIgnoreCase("header")) {
                    headers.add(CatsHeader.fromHeaderParameter(param));
                }
            }
        }

        return headers;
    }
}