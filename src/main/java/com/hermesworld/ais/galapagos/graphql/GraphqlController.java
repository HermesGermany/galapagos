package com.hermesworld.ais.galapagos.graphql;

import com.hermesworld.ais.galapagos.applications.*;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import graphql.GraphQLContext;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
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
        List<KnownApplication> producerMetadataList = new ArrayList<>();

        for (String producerId : producerIds) {
            Optional<KnownApplication> producerMetadata = applicationsService.getKnownApplication(producerId);
            producerMetadata.ifPresent(producerMetadataList::add);
        }
        return producerMetadataList;
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

    @QueryMapping
    public List<KnownApplication> applicationsByEnvironmentId(@Argument String environmentId,
            GraphQLContext graphQLContext) {
        graphQLContext.put("applicationsEnvironmentId", environmentId);
        List<ApplicationMetadata> allApplications = applicationsService.getAllApplicationMetadata(environmentId);
        List<KnownApplication> applicationList = new ArrayList<>();
        for (ApplicationMetadata application : allApplications) {
            String applicationId = application.getApplicationId();
            Optional<KnownApplication> knownApplication = applicationsService.getKnownApplication(applicationId);
            knownApplication.ifPresent(applicationList::add);
        }
        return applicationList;
    }

    @SchemaMapping(typeName = "AllApplications", field = "subscriptions")
    public List<SubscriptionMetadata> getSubscriptionsOfApplication(
            @ContextValue(name = "applicationsEnvironmentId") String environmentId, KnownApplication application) {
        return subscriptionService.getSubscriptionsOfApplication(environmentId, application.getId(), false);
    }

    @SchemaMapping(typeName = "AllApplications", field = "developers")
    public List<String> getDevelopers(KnownApplication application) {
        List<ApplicationOwnerRequest> allOwnerRequests = applicationsService.getAllApplicationOwnerRequests();
        return allOwnerRequests.stream()
                .filter(request -> request.getApplicationId().equals(application.getId())
                        && request.getState().equals(RequestState.APPROVED))
                .map(ApplicationOwnerRequest::getUserName).collect(Collectors.toList());
    }

    @SchemaMapping(typeName = "AllApplications", field = "authenticationInfo")
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
