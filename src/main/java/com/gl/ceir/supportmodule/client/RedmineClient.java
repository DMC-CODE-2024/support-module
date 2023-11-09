package com.gl.ceir.supportmodule.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gl.ceir.supportmodule.Constants.ClientTypeEnum;
import com.gl.ceir.supportmodule.builder.CreateIssueRequestBuilder;
import com.gl.ceir.supportmodule.model.*;
import com.gl.ceir.supportmodule.repository.IssueRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Component
public class RedmineClient {
    private final Logger log = LogManager.getLogger(getClass());

    @Value("${redmine.base-url}")
    private String baseUrl;

    @Value("${redmine.registered-user-api-key}")
    private String unregisteredUserApiKey;
    @Value("${redmine.unregistered-user-api-key}")
    private String registeredUserApiKey;
    @Value("${redmine-project-id}")
    private int projectId;
    @Value("${redmine-tracker-id}")
    private int trackerId;
    @Value("${redmine-create-status-id}")
    private int createStatusId;
    @Value("${redmine-create-status-name}")
    private String createStatusName;
    @Value("${redmine-resolve-status-name}")
    private String resolveStatusName;

    @Autowired
    private IssueRepository issueRepository;

    private final RestTemplate restTemplate;

    public RedmineClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<IssueResponse> getIssueWithJournals(int issueId, ClientTypeEnum clientType, IssuesEntity issuesEntity) {
        String key = getClientApiKey(clientType);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Redmine-API-Key", key);

        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> responseEntity = restTemplate.exchange(baseUrl + "/issues/" + issueId + ".json?include=journals,attachments", HttpMethod.GET, requestEntity, String.class);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            RedmineResponse issue = objectMapper.readValue(responseEntity.getBody(), RedmineResponse.class);
            IssueResponse issueResponse = CreateIssueRequestBuilder.issueResponse(issue.getIssue(), issuesEntity);
            return new ResponseEntity<>(issueResponse, responseEntity.getStatusCode());
        } catch (IOException e) {
            log.error("exception while fetching issue for id: {}, ex: {}", issueId, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<IssueResponse> createIssue(CreateIssueRequest createIssueRequest, ClientTypeEnum clientType) {
        try {
            RedmineIssueRequest createRedmineIssueRequest = CreateIssueRequestBuilder.redmineCreateIssueRequest(createIssueRequest, projectId, trackerId, createStatusId);
            String key = getClientApiKey(clientType);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-Redmine-API-Key", key);

            HttpEntity<RedmineIssueRequest> requestEntity = new HttpEntity<>(createRedmineIssueRequest, headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(baseUrl + "/issues.json", HttpMethod.POST, requestEntity, String.class);

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                ObjectMapper mapper = new ObjectMapper();
                RedmineResponse createdIssue = mapper.readValue(responseEntity.getBody(), RedmineResponse.class);
                IssuesEntity issue = issueRepository.save(CreateIssueRequestBuilder.saveToDb(createIssueRequest, createdIssue.getIssue().getId(), createStatusName, clientType.name(), ClientInfo.getClientId()));
                return new ResponseEntity<>(CreateIssueRequestBuilder.issueResponse(createdIssue.getIssue(), issue), HttpStatus.CREATED);
            } else {
                return new ResponseEntity<>(new IssueResponse(), responseEntity.getStatusCode());
            }
        } catch (Exception e) {
            log.error("exception in creating issue: {}", e);
            return new ResponseEntity<>(new IssueResponse(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Void> updateIssue(IssuesEntity issuesEntity, RedmineIssueRequest updatedIssue, ClientTypeEnum clientType, boolean isResolved) {
        try {
            String key = getClientApiKey(clientType);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-Redmine-API-Key", key);

            HttpEntity<RedmineIssueRequest> requestEntity = new HttpEntity<>(updatedIssue, headers);

            ResponseEntity<Void> responseEntity = restTemplate.exchange(
                    baseUrl + "/issues/" + issuesEntity.getIssueId() + ".json",
                    HttpMethod.PUT,
                    requestEntity,
                    Void.class
            );

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                if(isResolved) {
                    issuesEntity.setStatus(resolveStatusName);
                    issuesEntity.setResolvedBy(ClientInfo.getClientId());
                    issueRepository.save(issuesEntity);
                }
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            } else {
                return new ResponseEntity<>(responseEntity.getStatusCode());
            }
        } catch (Exception e) {
            log.error("exception while updating issue for id: {}, ex: {}", issuesEntity.getIssueId(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<UploadResponse> uploadFile(String filename, byte[] fileContent, ClientTypeEnum clientType) {
        String key = getClientApiKey(clientType);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/octet-stream");
        headers.set("X-Redmine-API-Key", key);

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileContent, headers);

        String uploadUrl = baseUrl + "/uploads.json?filename=" + filename;

        try {
            ResponseEntity<String> responseEntityString = restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);
            String responseBody = responseEntityString.getBody();
            System.out.println("Response Body: " + responseBody);

            // Parse the response into UploadResponse
            ObjectMapper mapper = new ObjectMapper();
            UploadResponse uploadResponse = mapper.readValue(responseBody, UploadResponse.class);

            return ResponseEntity.ok(uploadResponse);
        } catch (HttpClientErrorException e) {
            log.error("exception while uploading file: {}", e);
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                String errorMessage = "This file cannot be uploaded because it exceeds the maximum allowed file size (5 MB)";
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(UploadResponse.builder()
                        .upload(UploadResponse.UploadData.builder().message(errorMessage).build()).build());
            } else {
                return ResponseEntity.status(e.getStatusCode()).body(null);
            }
        } catch (Exception e) {
            log.error("exception while uploading file: {}", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    public String getClientApiKey(ClientTypeEnum type) {
        switch (type) {
            case END_USER:
                return unregisteredUserApiKey;
            case REGISTERED:
                return registeredUserApiKey;
            default:
                throw new IllegalArgumentException("Unknown client type: " + type);
        }
    }

    public ResponseEntity<RedmineResponse> getRedmineIssueWithJournals(String issueId, ClientTypeEnum clientType) {
        String key = getClientApiKey(clientType);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Redmine-API-Key", key);

        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> responseEntity = restTemplate.exchange(baseUrl + "/issues/" + issueId + ".json?include=journals,attachments", HttpMethod.GET, requestEntity, String.class);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            RedmineResponse issue = objectMapper.readValue(responseEntity.getBody(), RedmineResponse.class);
            return new ResponseEntity<>(issue, responseEntity.getStatusCode());
        } catch (IOException e) {
            log.error("exception while fetching issue for id: {}, ex: {}",issueId, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}

