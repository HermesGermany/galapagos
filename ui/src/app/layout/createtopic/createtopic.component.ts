import { Component, OnInit } from '@angular/core';
import { routerTransition } from 'src/app/router.animations';
import { ApplicationsService, BusinessCapabilityInfo, UserApplicationInfo } from 'src/app/shared/services/applications.service';
import { firstValueFrom, Observable } from 'rxjs';
import { TopicCreateParams, TopicsService, TopicType } from 'src/app/shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from 'src/app/shared/services/environments.service';
import { map, shareReplay, tap } from 'rxjs/operators';
import { ToastService } from 'src/app/shared/modules/toast/toast.service';
import { CustomLink, ServerInfoService } from '../../shared/services/serverinfo.service';
import { TopicSettingsData } from './datasettings/data-settings.component';
import { Router } from '@angular/router';
import { ApiKeyService } from '../../shared/services/apikey.service';
import { CertificateService } from '../../shared/services/certificates.service';
import { TranslateService } from '@ngx-translate/core';

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
                private certificateService: CertificateService,
                private translateService: TranslateService,
                private serverInfo: ServerInfoService, private router: Router) {
    }

    ngOnInit() {
        this.availableApplications = this.applicationsService.getUserApplications().getObservable();
        this.allEnvironments = this.environmentsService.getEnvironments();
        this.selectedEnvironment = this.environmentsService.getCurrentEnvironment().pipe(tap(() => this.checkAuthentication()))
            .pipe(shareReplay(1));
        firstValueFrom(this.topicsSerivce.getTopicCreateDefaults()).then(
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

    async checkAuthentication() {
        if (!this.selectedApplication || !this.selectedEnvironment) {
            return;
        }
        const isCcloud = await firstValueFrom(this.selectedEnvironment
            .pipe(map(env => env.authenticationMode === 'ccloud')));

        if (isCcloud) {
            try {
                const env = await firstValueFrom(this.selectedEnvironment);
                const apiKey = await this.apiKeyService.getApplicationApiKeysPromise(this.selectedApplication.id);
                this.showRegistrationWarning = !apiKey.authentications[env.id];
            } catch (e) {
                this.toasts.addHttpErrorToast('TOPIC_CREATED_CHECK_CERTIFICATES_ERROR', e);
            }
        } else {
            try {
                const env = await firstValueFrom(this.selectedEnvironment);
                const certificates = await this.certificateService.getApplicationCertificatesPromise(this.selectedApplication.id);

                this.showRegistrationWarning = !certificates.find(c => c.environmentId === env.id);
            } catch (e) {
                this.toasts.addHttpErrorToast('TOPIC_CREATED_CHECK_CERTIFICATES_ERROR', e);
            }
        }
        this.topicName = null;
        this.selectedBusinessCapability = null;
    }

    selectEnvironment(envId: string) {
        firstValueFrom(this.allEnvironments).then(
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

        return firstValueFrom(this.environmentsService.getCurrentEnvironment()).then(env =>
            this.topicsSerivce.createTopic(topicType, app, env.id, topicName, description, subscriptionApprovalRequired, initialSettings,
                this.createParams).then(
                () => {
                    this.toasts.addSuccessToast('TOPIC_CREATED');

                    if (topicType !== 'INTERNAL' && initialSchema) {
                        return this.createInitialSchema(topicName, env.id, initialSchema);
                    }
                    this.router.navigate(['/topics']);
                },
                err => this.toasts.addHttpErrorToast('TOPIC_CREATED_FAILED', err))

        );
    }

    private async createInitialSchema(topicName: string, environmentId: string, jsonSchema: string): Promise<any> {
        return this.topicsSerivce.addTopicSchema(topicName, environmentId, jsonSchema, true).then(
            () => {
                this.toasts.addSuccessToast('TOPIC_CREATED_JSON_SUCCESS');
            },
            err => this.toasts.addHttpErrorToast('TOPIC_CREATED_JSON_ERROR', err));
    }

}
