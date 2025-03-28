import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { ApplicationInfo, ApplicationsService, UserApplicationInfo } from '../../shared/services/applications.service';
import { firstValueFrom, Observable } from 'rxjs';
import {
    Change,
    EnvironmentsService,
    KafkaEnvironment,
    Staging,
    StagingResult
} from '../../shared/services/environments.service';
import { map } from 'rxjs/operators';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TranslateService } from '@ngx-translate/core';

interface SelectableChange {
    change: Change;

    selected: boolean;

}

@Component({
    selector: 'app-staging',
    templateUrl: './staging.component.html',
    styleUrls: ['./staging.component.scss'],
    animations: [routerTransition()]
})
export class StagingComponent implements OnInit {

    selectedApplication: UserApplicationInfo;

    availableApplications: Observable<UserApplicationInfo[]>;

    knownApplicationsSnapshot: ApplicationInfo[];

    selectedEnvironment: KafkaEnvironment;

    targetEnvironment: string;

    availableEnvironments: Observable<KafkaEnvironment[]>;

    staging: Staging = null;

    changes: SelectableChange[] = [];

    performing: boolean;

    stagingResult: StagingResult[] = [];

    translateParams: any = {};

    constructor(private applicationsService: ApplicationsService, private environmentsService: EnvironmentsService,
                private toasts: ToastService, private translate: TranslateService) {
    }

    ngOnInit() {
        this.availableApplications = this.applicationsService.getUserApplications().getObservable();
        this.availableEnvironments = this.environmentsService.getEnvironments().pipe(map(envs => envs.filter(env => !env.production)));
        firstValueFrom(this.environmentsService.getCurrentEnvironment()).then(env => {
            this.selectedEnvironment = env;
            this.updateTargetEnvironment();
        });
    }

    updateTargetEnvironment() {
        if (!this.selectedEnvironment) {
            this.targetEnvironment = '';
        } else {
            firstValueFrom(this.environmentsService.getEnvironments()).then(envs => {
                const idx = envs.findIndex(env => env === this.selectedEnvironment);
                if (idx > -1 && idx < envs.length - 1) {
                    this.targetEnvironment = envs[idx + 1].name;
                } else {
                    this.targetEnvironment = '';
                }
                this.translateParams.environmentName = this.targetEnvironment;
            });
        }
    }

    resetStagingResult() {
        this.changes = [];
        this.staging = null;
        this.stagingResult = [];
    }

    async prepareStaging(): Promise<any> {
        this.staging = null;
        this.stagingResult = [];

        this.knownApplicationsSnapshot = await firstValueFrom(this.applicationsService.getAvailableApplications(false));

        return this.environmentsService.prepareStaging(this.selectedApplication.id, this.selectedEnvironment).then(
            s => {
                this.staging = s;
                this.changes = s.changes.map(change => ({ change: change, selected: true }));
            },
            err => this.toasts.addHttpErrorToast('STAGING_CALCULATION_ERROR', err));
    }

    async performStaging(): Promise<any> {
        this.performing = true;
        this.stagingResult = [];

        const selectedChanges = this.changes.filter(c => c.selected).map(c => c.change);
        if (!selectedChanges.length) {
            return;
        }

        return this.environmentsService.performStaging(this.selectedApplication.id, this.selectedEnvironment, selectedChanges).then(
            result => {
                this.stagingResult = result;
                const totalSuccess = !result.find(r => !r.stagingSuccessful);
                const totalFailure = !result.find(r => r.stagingSuccessful);
                if (totalSuccess) {
                    this.toasts.addSuccessToast('CHANGES_ON_TARGET_CLUSTER_SUCCESS');
                } else if (!totalFailure) {
                    this.toasts.addWarningToast('Die Änderungen wurden durchgeführt.' +
                        ' Einzelne Änderungen konnten nicht durchgeführt werden.');
                } else {
                    this.toasts.addErrorToast('CHANGES_ON_TARGET_CLUSTER_ERROR');
                }
            },
            err => this.toasts.addHttpErrorToast('CHANGES_STAGING_ERROR', err))
            .finally(() => {
                this.performing = false;
                this.staging = null;
            });
    }

    stagingText(change: any, isResult: boolean = false): string {
        const changeType = change.changeType;

        switch (changeType) {

            case 'TOPIC_CREATED':
                return this.translate.instant('TOPIC_CREATED_STAGING', { topicName: change.topicMetadata.name });
            case 'TOPIC_SUBSCRIBED':
                return this.translate.instant('TOPIC_SUBSCRIBED_STAGING', { topicName: change.subscriptionMetadata.topicName });
            case 'TOPIC_UNSUBSCRIBED':
                return this.translate.instant('TOPIC_UNSUBSCRIBED_STAGING', { topicName: change.subscriptionMetadata.topicName });
            case 'TOPIC_DELETED':
                return this.translate.instant('TOPIC_DELETED_STAGING', { topicName: change.topicName });
            case 'TOPIC_DESCRIPTION_CHANGED':
                return this.translate.instant('TOPIC_DESCRIPTION_CHANGED_STAGING', { topicName: change.topicName });
            case 'TOPIC_SCHEMA_VERSION_PUBLISHED':
                const schemaVersion = change.schemaMetadata.schemaVersion;
                return this.translate.instant('TOPIC_SCHEMA_VERSION_PUBLISHED_STAGING', {
                    topicName: change.topicName,
                    schemaVersion: schemaVersion
                });
            case 'TOPIC_PRODUCER_APPLICATION_ADDED':
                return this.translate.instant('TOPIC_PRODUCER_APPLICATION_ADDED_STAGING', {
                    topicName: change.topicName,
                    producer: this.applicationName(change.producerApplicationId)
                });
            case 'TOPIC_PRODUCER_APPLICATION_REMOVED':
                return this.translate.instant('TOPIC_PRODUCER_APPLICATION_REMOVED_STAGING', {
                    topicName: change.topicName,
                    producer: this.applicationName(change.producerApplicationId)
                });
            case 'TOPIC_SUBSCRIPTION_APPROVAL_REQUIRED_FLAG_UPDATED':
                return this.translate.instant('TOPIC_SUBSCRIPTION_APPROVAL_REQUIRED_FLAG_UPDATED_STAGING',
                    { topicName: change.topicMetadata.name });
            case 'COMPOUND_CHANGE':
                if (isResult && change.additionalChanges && change.additionalChanges.length) {
                    return this.stagingText(change.mainChange) + this.translate.instant('COMPOUND_CHANGE_COUNT', { count: change.additionalChanges.length });
                }
                return this.stagingText(change.mainChange);
        }
        return this.translate.instant('OTHER_CHANGE_STAGING', { changeType: changeType });
    }

    private applicationName(applicationId: string): string {
        const apps = this.knownApplicationsSnapshot.filter(app => app.id === applicationId);
        return apps.length === 0 ? applicationId : apps[0].name;
    }
}
