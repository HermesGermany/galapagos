package com.hermesworld.ais.galapagos.graphql;

import com.hermesworld.ais.galapagos.applications.*;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import graphql.GraphQLContext;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class GraphqlController {

    private final TopicService topicService;

    private final ApplicationsService applicationsService;

    private final SubscriptionService subscriptionService;

    public GraphqlController(TopicService topicService, ApplicationsService applicationsService,
            SubscriptionService subscriptionService) {
        this.topicService = topicService;
        this.applicationsService = applicationsService;
        this.subscriptionService = subscriptionService;
    }

    @QueryMapping
    public List<TopicMetadata> topicsByType(@Argument String environmentId, @Argument TopicType topicType,
            GraphQLContext graphQLContext) {
        if (environmentId == null || topicType == null) {
            throw new IllegalArgumentException(
                    "The environmentId and topicType parameters are required and cannot be null.");
        }
        graphQLContext.put("environmentId", environmentId);
        return topicService.listTopics(environmentId).stream().filter(m -> m.getType().equals(topicType))
                .collect(Collectors.toList());
    }

    @SchemaMapping(typeName = "Topic", field = "ownerApplication")
    public Optional<KnownApplication> getOwnerApplication(TopicMetadata topic) {
        return applicationsService.getKnownApplication(topic.getOwnerApplicationId());
    }

    @SchemaMapping(typeName = "Topic", field = "producers")
    public List<KnownApplication> getProducers(TopicMetadata topic) {
        List<String> producerIds = topic.getProducers();
        return producerIds.stream().map(applicationsService::getKnownApplication).filter(Optional::isPresent)
                .map(Optional::get).toList();
    }

    @SchemaMapping(typeName = "Topic", field = "subscriptions")
    public List<SubscriptionMetadata> getSubscriptions(@ContextValue(name = "environmentId") String environmentId,
            TopicMetadata topic) {
        return subscriptionService.getSubscriptionsForTopic(environmentId, topic.getName(), false);
    }

    @SchemaMapping(typeName = "TopicSubscription", field = "clientApplication")
    public Optional<KnownApplication> getClientApplication(SubscriptionMetadata subscriptionMetadata) {
        return applicationsService.getKnownApplication(subscriptionMetadata.getClientApplicationId());
    }

    @SchemaMapping(typeName = "Topic", field = "schemas")
    public List<SchemaMetadata> getSchemas(@ContextValue(name = "environmentId") String environmentId,
            TopicMetadata topic) {
        return topicService.getTopicSchemaVersions(environmentId, topic.getName());
    }

    @QueryMapping
    public List<KnownApplication> applicationsByEnvironmentId(@Argument String environmentId,
            GraphQLContext graphQLContext) {
        if (environmentId == null) {
            throw new IllegalArgumentException("The environmentId parameter is required and cannot be null.");
        }
        graphQLContext.put("applicationsEnvironmentId", environmentId);
        List<ApplicationMetadata> allApplications = applicationsService.getAllApplicationMetadata(environmentId);

        return allApplications.stream().map(meta -> applicationsService.getKnownApplication(meta.getApplicationId()))
                .filter(Optional::isPresent).map(Optional::get).toList();
    }

    @SchemaMapping(typeName = "RegisteredApplication", field = "subscriptions")
    public List<SubscriptionMetadata> getSubscriptionsOfApplication(
            @ContextValue(name = "applicationsEnvironmentId") String environmentId, KnownApplication application) {
        return subscriptionService.getSubscriptionsOfApplication(environmentId, application.getId(), false);
    }

    @SchemaMapping(typeName = "RegisteredApplication", field = "developers")
    public List<String> getDevelopers(KnownApplication application) {
        List<ApplicationOwnerRequest> allOwnerRequests = applicationsService.getAllApplicationOwnerRequests();
        return allOwnerRequests.stream()
                .filter(request -> request.getApplicationId().equals(application.getId())
                        && request.getState().equals(RequestState.APPROVED))
                .map(ApplicationOwnerRequest::getUserName).collect(Collectors.toList());
    }

    @SchemaMapping(typeName = "RegisteredApplication", field = "authenticationInfo")
    public String getAuthenticationInfo(@ContextValue(name = "applicationsEnvironmentId") String environmentId,
            KnownApplication application) {
        Optional<ApplicationMetadata> applicationMetadataOpt = applicationsService.getApplicationMetadata(environmentId,
                application.getId());

        if (applicationMetadataOpt.isPresent()) {
            ApplicationMetadata applicationMetadata = applicationMetadataOpt.get();
            String authenticationJson = applicationMetadata.getAuthenticationJson();

            if (authenticationJson != null && !authenticationJson.isEmpty()) {
                return authenticationJson;
            }
        }
        return null;
    }
}
