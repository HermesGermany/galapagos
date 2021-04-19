import { Injectable } from '@angular/core';
import { ApplicationInfo, ApplicationsService, BusinessCapabilityInfo, UserApplicationInfo } from './applications.service';
import { HttpClient } from '@angular/common/http';
import { concatMap, map, take } from 'rxjs/operators';
import { jsonHeader, ReplayContainer } from './services-common';
import { combineLatest, forkJoin, Observable, of } from 'rxjs';
import { EnvironmentsService, KafkaEnvironment } from './environments.service';
import { TopicSettingsData } from '../../layout/createtopic/datasettings/data-settings.component';

export type TopicType = 'EVENTS' | 'DATA' | 'COMMANDS' | 'INTERNAL';

export type SubscriptionState = 'APPROVED' | 'PENDING' | 'REJECTED';

export interface Topic {
    name: string;

    topicType: TopicType;

    environmentId: string;

    description: string;

    ownerApplication: ApplicationInfo;

    createdTimestamp: string;

    deprecated: boolean;

    deprecationText: string;

    eolDate: string;

    subscriptionApprovalRequired: boolean;

    deletable: boolean;
}

export interface TopicSubscription {
    id: string;

    clientApplication: ApplicationInfo;

    canDelete: boolean;

    state: SubscriptionState;
}

export interface SchemaMetadata {
    id: string;

    topicName: string;

    createdBy: string;

    createdAt: string;

    schemaVersion: number;

    jsonSchema: string;

    changeDescription: string;

    isLatest?: boolean;

}

export interface TopicConfigValues {

    [key: string]: string;

}

export interface TopicCreateParams {

    partitionCount: number;

    topicConfig: TopicConfigValues;

}

export interface TopicConfigDescriptor {

    configName: string;

    configDescription?: string;

}

export interface TopicCreateDefaults {

    defaultPartitionCount: number;

    defaultTopicConfigs: { [key: string]: string };

    topicNameSuggestion?: string;

}

export interface TopicUpdateConfigValue {

    name: string;

    value: string;
}

export interface TopicRecord {

    partition: number;

    offset: number;

    key: string;

    value: string;
}

@Injectable()
export class TopicsService {

    // TODO if there are too many topics, we should optimize with e.g. pagination
    private topicsList = new ReplayContainer<Topic[]>(() => of([]));

    private supportedConfigs = new ReplayContainer<TopicConfigDescriptor[]>(() => this.http.get('/api/util/supported-kafka-configs'));

    private createDefaults = new ReplayContainer<TopicCreateDefaults>(() =>
        this.http.post('/api/util/topic-create-defaults', JSON.stringify({}), { headers: jsonHeader() }));

    private defaultTopicConfigs: { [env: string]: ReplayContainer<TopicConfigValues> } = {};

    private currentEnvironment: KafkaEnvironment;

    constructor(private http: HttpClient, private applicationsService: ApplicationsService,
                private environmentsService: EnvironmentsService) {
        environmentsService.getCurrentEnvironment().subscribe({
            next: env => {
                this.topicsList.setRefresher(this.buildTopicsRefresher(env.id));
                this.topicsList.refresh().then();
                this.currentEnvironment = env;
            }
        });
    }

    public deprecateTopic(deprecatedDescription: string, eolDate: string, topicName: string): Promise<any> {
        const body = JSON.stringify({
            deprecationText: deprecatedDescription,
            eolDate: eolDate
        });
        return this.http.post('/api/topics/' + this.currentEnvironment.id + '/' + topicName, body, { headers: jsonHeader() })
            .toPromise().then(() => this.topicsList.refresh());
    }

    public unDeprecateTopic(topicName: string): Promise<any> {
        return this.http.post('/api/topics/' + this.currentEnvironment.id + '/' + topicName, {}, { headers: jsonHeader() })
            .toPromise().then(() => this.topicsList.refresh());
    }

    public updateTopicDescription(updatedTopicDescription: string, topicName: string): Promise<any> {
        const body = JSON.stringify({
            description: updatedTopicDescription,
            updateDescription: true
        });

        return this.http.post('/api/topics/' + this.currentEnvironment.id + '/' + topicName, body, { headers: jsonHeader() })
            .toPromise().then(() => this.topicsList.refresh());
    }

    public getTopicNameSuggestion(topicType: TopicType, appInfo: UserApplicationInfo,
        businessCapability: BusinessCapabilityInfo): Promise<string> {
        // TODO this is server-side business logic...
        if (topicType !== 'INTERNAL' && !businessCapability) {
            return Promise.resolve('');
        }

        const body = JSON.stringify({
            topicType: topicType,
            applicationId: appInfo.id,
            environmentId: this.currentEnvironment.id,
            businessCapabilityId: businessCapability ? businessCapability.id : null
        });

        return this.http.post('/api/util/topicname', body, { headers: jsonHeader() }).pipe(map(data => data['name'])).toPromise();
    }

    public async createTopic(topicType: TopicType, appInfo: UserApplicationInfo, environmentId: string, topicName: string,
        description: string, subscriptionApprovalRequired: boolean, initialSettings: TopicSettingsData,
        createParams: TopicCreateParams): Promise<any> {

        let topicSettingsData;
        if (topicType !== 'INTERNAL') {
            createParams.topicConfig = {
                'cleanup.policy': initialSettings.cleanUpStrategy.join(),
                'retention.ms': initialSettings.retentionTimeMillis.toString(),
                'min.compaction.lag.ms': initialSettings.compactionTimeMillis.toString(),
                'delete.retention.ms': initialSettings.compactionTimeMillis.toString()
            };

            topicSettingsData = {
                compactionTimeMillis: initialSettings.compactionTimeMillis,
                retentionTimeMillis: initialSettings.retentionTimeMillis,
                criticality: initialSettings.criticality,
                messagesPerDay: initialSettings.messagesPerDay,
                messagesSize: initialSettings.messagesSize
            };
        }

        const body = JSON.stringify({
            name: topicName,
            topicType: topicType,
            ownerApplicationId: appInfo.id,
            subscriptionApprovalRequired: subscriptionApprovalRequired,
            description: description || null,
            ...createParams,
            ...topicSettingsData
        });

        return this.http.put('/api/topics/' + environmentId, body, { headers: jsonHeader() }).toPromise()
            .then(() => this.topicsList.refresh());
    }

    public async deleteTopic(environmentId: string, topicName: string): Promise<any> {
        return this.http.delete('/api/topics/' + environmentId + '/' + topicName).toPromise().then(() => this.topicsList.refresh());
    }

    public listTopics(): ReplayContainer<Topic[]> {
        return this.topicsList;
    }

    public getTopicSubscribers(topicName: string, environmentId: string): Observable<TopicSubscription[]> {
        const appsObs = this.applicationsService.getAvailableApplications(false).pipe(take(1));
        const userAppsObs = this.applicationsService.getUserApplications().getObservable().pipe(take(1));
        const envObs = this.environmentsService.getEnvironments().pipe(take(1))
            .pipe(map(envs => envs.find(env => env.id === environmentId)));

        const toTopicSubscription = (d: any, apps: ApplicationInfo[], userApps: UserApplicationInfo[],
            env: KafkaEnvironment): TopicSubscription => ({
            id: d.id as string,
            clientApplication: apps.find(a => a.id === d.clientApplicationId),
            canDelete: userApps.findIndex(a => a.id === d.clientApplicationId) > -1 && !env.stagingOnly,
            state: d.state as SubscriptionState
        });

        // valsArray receives available applications in [0], user applications in [1], and environment in [2]
        return forkJoin([appsObs, userAppsObs, envObs]).pipe(concatMap(valsArray =>
            this.http.get('/api/topics/' + environmentId + '/' + topicName + '/subscriptions?includeNonApproved=true')
                .pipe(map(val => {
                    const data = val as Array<any>;
                    return data.filter(d => d.environmentId === environmentId)
                        .map(d => toTopicSubscription(d, valsArray[0], valsArray[1], valsArray[2])).filter(s => s.clientApplication);
                }))
        ));
    }

    public getTopicSchemas(topicName: string, environmentId: string): Promise<SchemaMetadata[]> {
        return this.http.get('/api/schemas/' + environmentId + '/' + topicName).pipe(map(d => d as SchemaMetadata[]))
            .pipe(map(schemas => this.markLatest(schemas))).toPromise();
    }

    public addTopicSchema(topicName: string, environmentId: string, jsonSchema: string, schemaChangeDescription?: string): Promise<any> {
        const body = JSON.stringify({
            jsonSchema: jsonSchema,
            changeDescription: schemaChangeDescription ? schemaChangeDescription : null
        });

        return this.http.put('/api/schemas/' + environmentId + '/' + topicName, body, { headers: jsonHeader() }).toPromise();
    }

    public deleteLatestSchema(topicName: string, environmentId: string): Promise<any> {
        return this.http.delete('/api/schemas/' + environmentId + '/' + topicName, { headers: jsonHeader() }).toPromise();
    }

    public subscribeToTopic(topicName: string, environmentId: string, applicationId: string, description: string): Promise<any> {
        const body = JSON.stringify({
            topicName: topicName,
            description: description
        });

        return this.http.put('/api/applications/' + applicationId + '/subscriptions/' + environmentId, body, { headers: jsonHeader() })
            .pipe(take(1)).toPromise();
    }

    public unsubscribeFromTopic(environmentId: string, applicationId: string, subscriptionId: string): Promise<any> {
        return this.http.delete('/api/applications/' + applicationId + '/subscriptions/' + environmentId + '/' + subscriptionId)
            .toPromise();
    }

    public updateTopicSubscription(environmentId: string, topicName: string, subscriptionId: string, approved: boolean): Promise<any> {
        const body = JSON.stringify({
            newState: approved ? 'APPROVED' : 'REJECTED'
        });
        return this.http.post('/api/topics/' + environmentId + '/' + topicName + '/subscriptions/' + subscriptionId,
            body, { headers: jsonHeader() }).toPromise();
    }

    public getSupportedConfigProperties(): Observable<TopicConfigDescriptor[]> {
        return this.supportedConfigs.getObservable();
    }

    public getTopicConfig(topicName: string, environmentId: string): Observable<TopicConfigValues> {
        return this.http.get('/api/topicconfigs/' + environmentId + '/' + topicName).pipe(map(vals =>
            (vals as Array<any>).reduce((pv, cv) => {
                pv[cv.name] = cv.value;
                return pv;
            }, {})));
    }

    public getDefaultTopicConfig(environmentId: string): Observable<TopicConfigValues> {
        if (!this.defaultTopicConfigs[environmentId]) {
            this.defaultTopicConfigs[environmentId] = new ReplayContainer<TopicConfigValues>(
                () => this.http.get('/api/util/default-topic-config/' + environmentId));
        }
        return this.defaultTopicConfigs[environmentId].getObservable();
    }

    public getEnvironmentsForTopic(topicName: string): Observable<string[]> {
        return this.http.get('/api/util/environments-for-topic/' + topicName).pipe(map(v => v as string[]));
    }

    public getTopicCreateDefaults(): Observable<TopicCreateDefaults> {
        return this.createDefaults.getObservable();
    }

    public async updateTopicConfig(topicName: string, environmentId: string, config: TopicUpdateConfigValue[]): Promise<any> {
        return this.http.post('/api/topicconfigs/' + environmentId + '/' + topicName,
            JSON.stringify(config), { headers: jsonHeader() }).toPromise();
    }

    public getTopicData(topicName: string, environmentId: string): Promise<TopicRecord[]> {
        return this.http.get('/api/util/peek-data/' + environmentId + '/' + topicName).pipe(map(d => d as TopicRecord[])).toPromise();
    }

    private buildTopicsRefresher(environmentId: string): () => Observable<Topic[]> {
        const toTopicArray = (values: [any, ApplicationInfo[]]) => {
            const arr: Array<any> = values[0] as Array<any>;
            const apps = values[1];

            const result: Topic[] = arr.map(a => ({
                name: a.name,
                topicType: a.topicType,
                environmentId: a.environmentId,
                description: a.description,
                createdTimestamp: a.createdTimestamp,
                deprecated: a.deprecated,
                deprecationText: a.deprecationText,
                eolDate: a.eolDate,
                ownerApplication: apps.find(app => app.id === a.ownerApplicationId) || null,
                subscriptionApprovalRequired: a.subscriptionApprovalRequired,
                deletable: a.deletable
            }));

            return result;
        };

        return () => combineLatest([this.http.get('/api/topics/' + environmentId + '?includeInternal=true'),
            this.applicationsService.getAvailableApplications(false)]).pipe(map(values => toTopicArray(values))).pipe(take(1));
    }

    private markLatest(schemas: SchemaMetadata[]): SchemaMetadata[] {
        schemas.forEach(s => s.isLatest = false);
        if (schemas.length) {
            schemas[schemas.length - 1].isLatest = true;
        }
        return schemas;
    }

}
