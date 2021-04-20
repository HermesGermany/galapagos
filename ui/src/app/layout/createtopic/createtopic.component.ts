import { Component, OnInit } from '@angular/core';
import { routerTransition } from 'src/app/router.animations';
import {
    ApplicationsService,
    BusinessCapabilityInfo,
    UserApplicationInfo
} from 'src/app/shared/services/applications.service';
import { Observable } from 'rxjs';
import { TopicCreateParams, TopicsService, TopicType } from 'src/app/shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from 'src/app/shared/services/environments.service';
import { map, shareReplay, take, tap } from 'rxjs/operators';
import { ToastService } from 'src/app/shared/modules/toast/toast.service';
import { CertificateService } from 'src/app/shared/services/certificates.service';
import { CustomLink, ServerInfoService } from '../../shared/services/serverinfo.service';

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

    subscriptionApprovalRequired = false;

    availableApplications: Observable<UserApplicationInfo[]>;

    allEnvironments: Observable<KafkaEnvironment[]>;

    selectedEnvironment: Observable<KafkaEnvironment>;

    initialSchema: string;

    createParams: TopicCreateParams = { partitionCount: 6, topicConfig: {} };

    showRegistrationWarning = false;

    showSubscriptionApprovalRequired: Observable<boolean>;

    namingConventionLink: Observable<CustomLink>;

    constructor(private topicsSerivce: TopicsService, private applicationsService: ApplicationsService,
                private environmentsService: EnvironmentsService, private toasts: ToastService,
                private certificateService: CertificateService, private serverInfo: ServerInfoService) {
    }

    ngOnInit() {
        this.availableApplications = this.applicationsService.getUserApplications().getObservable();
        this.allEnvironments = this.environmentsService.getEnvironments();
        this.selectedEnvironment = this.environmentsService.getCurrentEnvironment().pipe(tap(() => this.checkApplicationCertificate()))
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

    async checkApplicationCertificate() {
        if (!this.selectedApplication || !this.selectedEnvironment) {
            return;
        }
        try {
            const certificates = await this.certificateService.getApplicationCertificatesPromise(this.selectedApplication.id);
            const env = await this.selectedEnvironment.pipe(take(1)).toPromise();
            this.showRegistrationWarning = !certificates.find(c => c.environmentId === env.id);
        } catch (e) {
            this.toasts.addHttpErrorToast('Could not check for application certificates', e);
        }
    }

    selectEnvironment(envId: string) {
        this.allEnvironments.pipe(take(1)).toPromise().then(
            envs => this.environmentsService.setCurrentEnvironment(envs.find(env => env.id === envId)));
    }

    async createTopic(): Promise<any> {
        // save relevant values to avoid concurrent changes by the user
        const topicType = this.topicType;
        const topicName = this.topicName;
        const description = this.description;
        const subscriptionApprovalRequired = this.topicType !== 'INTERNAL' && this.subscriptionApprovalRequired;
        const app = this.selectedApplication;
        const initialSchema = this.initialSchema;

        return this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise().then(env =>
            this.topicsSerivce.createTopic(topicType, app, env.id, topicName, description, subscriptionApprovalRequired,
                this.createParams).then(
                () => {
                    this.toasts.addSuccessToast('Das Topic wurde erfolgreich angelegt.');
                    this.selectedApplication = null;
                    this.selectedBusinessCapability = null;
                    this.topicType = null;
                    this.description = null;
                    this.topicName = null;
                    this.subscriptionApprovalRequired = false;
                    if (topicType !== 'INTERNAL' && initialSchema) {
                        return this.createInitialSchema(topicName, env.id, initialSchema);
                    }
                    // TODO perhaps navigate to "browse topics"?
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
