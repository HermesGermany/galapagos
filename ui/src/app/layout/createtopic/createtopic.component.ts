import { Component, OnInit } from '@angular/core';
import { routerTransition } from 'src/app/router.animations';
import { ApplicationsService, BusinessCapabilityInfo, UserApplicationInfo } from 'src/app/shared/services/applications.service';
import { Observable } from 'rxjs';
import { TopicCreateParams, TopicsService, TopicType } from 'src/app/shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from 'src/app/shared/services/environments.service';
import { map, shareReplay, take, tap } from 'rxjs/operators';
import { ToastService } from 'src/app/shared/modules/toast/toast.service';
import { CustomLink, ServerInfoService } from '../../shared/services/serverinfo.service';
import { TopicSettingsData } from './datasettings/data-settings.component';
import { Router } from '@angular/router';
import { ApiKeyService } from '../../shared/services/apikey.service';

@Component({
    selector: 'app-create-topic',
    templateUrl: './createtopic.component.html',
    styleUrls: ['./createtopic.component.scss'],
    animations: [routerTransition()]
})
export class CreateTopicComponent implements OnInit {

    selectedApplication: UserApplicationInfo;

    selectedBusinessCapability: BusinessCapabilityInfo;

    topicName: string;

    topicType: TopicType;

    description: string;

    availableApplications: Observable<UserApplicationInfo[]>;

    allEnvironments: Observable<KafkaEnvironment[]>;

    selectedEnvironment: Observable<KafkaEnvironment>;

    initialSchema: string;

    createParams: TopicCreateParams = { partitionCount: 6, topicConfig: {} };

    showRegistrationWarning = false;

    showSubscriptionApprovalRequired: Observable<boolean>;

    namingConventionLink: Observable<CustomLink>;

    constructor(private topicsSerivce: TopicsService,
                private applicationsService: ApplicationsService,
                private environmentsService: EnvironmentsService,
                private toasts: ToastService, private apiKeyService: ApiKeyService,
                private serverInfo: ServerInfoService, private router: Router) {
    }

    ngOnInit() {
        this.availableApplications = this.applicationsService.getUserApplications().getObservable();
        this.allEnvironments = this.environmentsService.getEnvironments();
        this.selectedEnvironment = this.environmentsService.getCurrentEnvironment().pipe(tap(() => this.checkApplicationApiKey()))
            .pipe(shareReplay(1));
        this.topicsSerivce.getTopicCreateDefaults().pipe(take(1)).toPromise().then(
            val => this.createParams.partitionCount = val.defaultPartitionCount);

        this.showSubscriptionApprovalRequired = this.serverInfo.getServerInfo()
            .pipe(map(info => info.toggles && info.toggles.subscriptionApproval === 'true'));

        this.namingConventionLink = this.serverInfo.getUiConfig().pipe(map(config =>
            config.customLinks.find(link => link.id === 'naming-convention')));
    }

    async suggestTopicName(): Promise<any> {
        if (this.selectedApplication && this.topicType) {
            return this.topicsSerivce.getTopicNameSuggestion(this.topicType, this.selectedApplication,
                this.selectedBusinessCapability).then(name => this.topicName = name, () => this.topicName = '');
        }
        return Promise.resolve(null);
    }

    async checkApplicationApiKey() {
        if (!this.selectedApplication || !this.selectedEnvironment) {
            return;
        }
        try {
            const env = await this.selectedEnvironment.pipe(take(1)).toPromise();
            const apiKey = await this.apiKeyService.getApplicationApiKeysPromise(this.selectedApplication.id, env.id);
            this.showRegistrationWarning = !apiKey.find(c => c.environmentId === env.id);
        } catch (e) {
            this.toasts.addHttpErrorToast('Could not check for application certificates', e);
        }
    }

    selectEnvironment(envId: string) {
        this.allEnvironments.pipe(take(1)).toPromise().then(
            envs => this.environmentsService.setCurrentEnvironment(envs.find(env => env.id === envId)));
    }

    async createTopic(initialSettings: TopicSettingsData): Promise<any> {
        // save relevant values to avoid concurrent changes by the user
        const topicType = this.topicType;
        const topicName = this.topicName;
        const description = this.description;
        const subscriptionApprovalRequired = this.topicType !== 'INTERNAL' && initialSettings.subscriptionApprovalRequired;
        const app = this.selectedApplication;
        const initialSchema = this.initialSchema;

        return this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise().then(env =>
            this.topicsSerivce.createTopic(topicType, app, env.id, topicName, description, subscriptionApprovalRequired, initialSettings,
                this.createParams).then(
                () => {
                    this.toasts.addSuccessToast('Das Topic wurde erfolgreich angelegt.');

                    if (topicType !== 'INTERNAL' && initialSchema) {
                        return this.createInitialSchema(topicName, env.id, initialSchema);
                    }
                    this.router.navigate(['/topics']);
                },
                err => this.toasts.addHttpErrorToast('Das Topic konnte nicht angelegt werden', err))
        );
    }

    private async createInitialSchema(topicName: string, environmentId: string, jsonSchema: string): Promise<any> {
        return this.topicsSerivce.addTopicSchema(topicName, environmentId, jsonSchema).then(
            () => {
                this.toasts.addSuccessToast('Das initiale JSON Schema wurde erfolgreich veröffentlicht.');
            },
            err => this.toasts.addHttpErrorToast('Das initiale JSON Schema konnte nicht veröffentlicht werden', err)
        );
    }

}
