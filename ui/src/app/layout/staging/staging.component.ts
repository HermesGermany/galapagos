import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { ApplicationInfo, ApplicationsService, UserApplicationInfo } from '../../shared/services/applications.service';
import { Observable } from 'rxjs';
import { Change, EnvironmentsService, KafkaEnvironment, Staging, StagingResult } from '../../shared/services/environments.service';
import { map, take } from 'rxjs/operators';
import { ToastService } from '../../shared/modules/toast/toast.service';

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

    registeredApplicationsSnapshot: ApplicationInfo[];

    selectedEnvironment: KafkaEnvironment;

    targetEnvironment: string;

    availableEnvironments: Observable<KafkaEnvironment[]>;

    staging: Staging = null;

    changes: SelectableChange[] = [];

    performing: boolean;

    stagingResult: StagingResult[] = [];

    translateParams: any = {};

    constructor(private applicationsService: ApplicationsService, private environmentsService: EnvironmentsService,
                private toasts: ToastService) {
    }

    ngOnInit() {
        this.availableApplications = this.applicationsService.getUserApplications().getObservable();
        this.availableEnvironments = this.environmentsService.getEnvironments().pipe(map(envs => envs.filter(env => !env.production)));
        this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise().then(env => {
            this.selectedEnvironment = env;
            this.updateTargetEnvironment();
        });
    }

    updateTargetEnvironment() {
        if (!this.selectedEnvironment) {
            this.targetEnvironment = '';
        } else {
            this.environmentsService.getEnvironments().pipe(take(1)).toPromise().then(envs => {
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

    async prepareStaging(): Promise<any> {
        this.staging = null;
        this.stagingResult = [];

        this.registeredApplicationsSnapshot = await this.environmentsService.getRegisteredApplications(this.selectedEnvironment.id);

        return this.environmentsService.prepareStaging(this.selectedApplication.id, this.selectedEnvironment).then(
            s => {
                this.staging = s;
                this.changes = s.changes.map(change => ({ change: change, selected: true }));
            },
            err => this.toasts.addHttpErrorToast('Could not calculate staging for this application', err));
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
                    this.toasts.addSuccessToast('Die Änderungen wurden erfolgreich auf dem Zielcluster durchgeführt.');
                } else if (!totalFailure) {
                    this.toasts.addWarningToast('Die Änderungen wurden durchgeführt.' +
                        ' Einzelne Änderungen konnten nicht durchgeführt werden.');
                } else {
                    this.toasts.addErrorToast('Die Änderungen konnten sämtlich nicht durchgeführt werden.');
                }
            },
            err => this.toasts.addHttpErrorToast('Could not perform staging for this application', err))
            .finally(() => {
                this.performing = false;
                this.staging = null;
            });
    }

    stagingText(change: any) {
        // TODO i18n
        const changeType = change.changeType;
        switch (changeType) {
        case 'TOPIC_CREATED':
            return 'Topic <code>' + change.topicMetadata.name + '</code> anlegen';
        case 'TOPIC_SUBSCRIBED':
            return 'Topic <code>' + change.subscriptionMetadata.topicName + '</code> abonnieren';
        case 'TOPIC_UNSUBSCRIBED':
            return 'Abonnement von Topic <code>' + change.subscriptionMetadata.topicName + '</code> kündigen';
        case 'TOPIC_DELETED':
            return 'Topic <code>' + change.topicName + '</code> löschen';
        case 'TOPIC_DESCRIPTION_CHANGED':
            return 'Beschreibung von Topic <code>' + change.topicName + '</code> aktualisieren';
        case 'TOPIC_DEPRECATED':
            return 'Topic <code>' + change.topicName + '</code> als deprecated markieren';
        case 'TOPIC_UNDEPRECATED':
            return 'Deprecated-Markierung von Topic <code>' + change.topicName + '</code> entfernen';
        case 'TOPIC_SCHEMA_VERSION_PUBLISHED':
            return 'Schema Version <code>' + change.schemaMetadata.schemaVersion + '</code> für Topic <code>'
                    + change.topicName + '</code> veröffentlichen';
        case 'TOPIC_PRODUCER_APPLICATION_ADDED':
            return `Produzent <code>${this.applicationInfo(change.topicProducerIds).name} ` +
                    `</code> hinzufügen für Topic ` + `<code>` + change.topicName + `  </code>`;
        case 'TOPIC_PRODUCER_APPLICATION_REMOVED':
            return `Produzent <code>${this.applicationInfo(change.topicProducerIds).name} ` + `</code> entfernen für Topic `
                    + `<code>` + change.topicName + `  </code>`;
        case 'TOPIC_SUBSCRIPTION_APPROVAL_REQUIRED_FLAG_UPDATED':
            return 'Für Topic <code>' + change.topicMetadata.name + '</code> die Freigabe von Abonnements erforderlich machen';
        case 'COMPOUND_CHANGE':
            return this.stagingText(change.mainChange);
        }
        return 'Änderung vom Typ <code>' + changeType + '</code> durchführen';
    }

    private applicationInfo(applicationId): ApplicationInfo {
        const apps = this.registeredApplicationsSnapshot.filter(app => app.id === applicationId);
        return apps.length === 0 ? null : apps[0];
    };

}
