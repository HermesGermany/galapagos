import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { ModalDismissReasons, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import {
    ApplicationInfo,
    ApplicationOwnerRequest,
    ApplicationPrefixes,
    ApplicationsService,
    UserApplicationInfo
} from '../../shared/services/applications.service';
import { combineLatest, Observable } from 'rxjs';
import { toNiceTimestamp } from '../../shared/util/time-util';

import * as moment from 'moment';
import { map, shareReplay, startWith } from 'rxjs/operators';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TopicsService } from '../../shared/services/topics.service';
import { TranslateService } from '@ngx-translate/core';
import { OpenApiKeyDialogEvent } from './application-block.component';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiKeyService } from '../../shared/services/apikey.service';

export interface UserApplicationInfoWithTopics extends UserApplicationInfo {

    owningTopics: string[];

    usingTopics: Observable<string[]>;

    prefixes: Observable<ApplicationPrefixes>;

}

@Component({
    selector: 'app-applications',
    templateUrl: './applications.component.html',
    styleUrls: ['./applications.component.scss'],
    animations: [routerTransition()]
})
export class ApplicationsComponent implements OnInit {
    availableApplications: Observable<ApplicationInfo[]>;

    userRequests: Observable<ApplicationOwnerRequest[]>;

    environments: Observable<KafkaEnvironment[]>;

    selectedApplicationForRequest: ApplicationInfo = null;

    commentsForRequest = '';

    userApplications: Observable<UserApplicationInfoWithTopics[]>;

    apiKeyDlgData = {
        applicationName: null,
        applicationId: null,
        environment: null,
        existingApiKey: null
    };

    key: string;

    secret: string;

    showApiKeyTable = false;

    copiedKey = false;

    copiedSecret = false;

    submitting = false;

    appListLoading = true;

    showCopyCompleteText = false;

    currentLang: Observable<string>;

    constructor(
        private modalService: NgbModal,
        private applicationsService: ApplicationsService,
        private apiKeyService: ApiKeyService,
        private environmentsService: EnvironmentsService,
        private topicsService: TopicsService,
        private toasts: ToastService,
        private translateService: TranslateService
    ) {
    }

    ngOnInit() {
        const now = moment();

        this.currentLang = this.translateService.onLangChange.pipe(map(evt => evt.lang))
            .pipe(startWith(this.translateService.currentLang)).pipe(shareReplay(1));

        const relevant = (req: ApplicationOwnerRequest) => moment.utc(req.lastStatusChangeAt).subtract(30, 'days').isBefore(now);
        this.availableApplications = this.applicationsService.getAvailableApplications(true).pipe(
            map(val => {
                this.appListLoading = false;
                return val;
            })
        );
        this.userRequests = this.applicationsService
            .getUserApplicationOwnerRequests()
            .pipe(map(requests => requests.filter(req => relevant(req))));

        const obsTopics = this.topicsService.listTopics().getObservable();
        const obsPlainUserApplications = this.applicationsService.getUserApplications().getObservable();

        this.userApplications = combineLatest([obsTopics, obsPlainUserApplications, this.environmentsService.getCurrentEnvironment()])
            .pipe(map(([topics, apps, env]) => {
                const getOwningTopicsForApp = (appId: string): string[] =>
                    topics.filter(topic => topic.ownerApplication.id === appId && topic.topicType !== 'INTERNAL').map(t => t.name);

                return apps.map(app => ({
                    ...app, owningTopics: getOwningTopicsForApp(app.id),
                    usingTopics: this.applicationsService.getApplicationSubscriptions(app.id, env.id)
                        .pipe(map(subs => subs.map(s => s.topicName))),
                    prefixes: this.applicationsService.getApplicationPrefixes(app.id, env.id)
                })) as UserApplicationInfoWithTopics[];
            })).pipe(shareReplay(1));

        this.environments = this.environmentsService.getEnvironments();

        this.applicationsService.refresh().then();
    }

    submitRequest(): Promise<any> {
        if (!this.selectedApplicationForRequest) {
            this.toasts.addErrorToast('Bitte wähle zunächst eine Anwendung aus.');
            return Promise.resolve();
        }

        this.submitting = true;
        return this.applicationsService
            .submitApplicationOwnerRequest(this.selectedApplicationForRequest.id, this.commentsForRequest)
            .then(
                () => {
                    this.selectedApplicationForRequest = null;
                    this.commentsForRequest = '';
                    this.toasts.addSuccessToast('Request wurde erfolgreich erstellt.');
                },
                err => this.toasts.addHttpErrorToast('Konnte Request nicht erstellen', err)
            )
            .finally(() => (this.submitting = false));
    }

    async cancelRequest(req: ApplicationOwnerRequest): Promise<any> {
        return this.applicationsService.cancelApplicationOwnerRequest(req.id).then(
            () => this.toasts.addSuccessToast('Request erfolgreich abgebrochen'),
            err => this.toasts.addHttpErrorToast('Konnte Request nicht abbrechen', err)
        );
    }

    openApiKeyDlg(event: OpenApiKeyDialogEvent, content: any) {
        const app = event.application;
        const env = event.environment;

        this.apiKeyDlgData = {
            applicationId: app.id,
            applicationName: app.name,
            environment: env,
            existingApiKey: null
        };
        this.apiKeyService.getApplicationApiKeysPromise(app.id, env.id).then(keys => {
            this.apiKeyDlgData.existingApiKey = keys.find(key => key.environmentId === env.id);

            this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg', windowClass: 'modal-xxl' }).result.then(result => {
            }, reason => {
                if (reason === ModalDismissReasons.BACKDROP_CLICK) {
                    this.handleDlgDismiss();
                }
            });
        });
    }

    generateApiKey(): void {
        if (this.apiKeyDlgData.applicationId && this.apiKeyDlgData.environment) {
            const appId = this.apiKeyDlgData.applicationId;
            const envId = this.apiKeyDlgData.environment;
            this.apiKeyService
                .requestApiKey(appId, envId)
                .then(apiKey => {
                        this.key = apiKey.apiKey;
                        this.secret = apiKey.apiSecret;
                        this.showApiKeyTable = true;
                        this.toasts.addSuccessToast('API Key erfolgreich erstellt');
                    },
                    (err: HttpErrorResponse) => this.toasts.addHttpErrorToast('API Key konnte nicht erstellt werden', err))
                .then(() => this.apiKeyService.getApplicationApiKeys(appId, envId).refresh());
        }
    }


    handleDlgDismiss(): void {
        this.showApiKeyTable = false;
        this.secret = null;

    }

    copyValue(value: string) {
        const selBox = document.createElement('textarea');
        selBox.style.position = 'fixed';
        selBox.style.left = '0';
        selBox.style.top = '0';
        selBox.style.opacity = '0';
        selBox.value = value;
        document.body.appendChild(selBox);
        selBox.focus();
        selBox.select();
        document.execCommand('copy');
        document.body.removeChild(selBox);

        console.log(value);

        if (value === this.key) {
            this.copiedKey = true;
        } else {
            this.copiedSecret = true;

        }
    }

    niceTimestamp(str: string): string {
        return toNiceTimestamp(str);
    }

    toAppName(appId: string): Observable<string> {
        // this is not as bad as it looks! ApplicationService returns a ReplaySubject
        // and does not issue a new AJAX request for every call here...
        return this.applicationsService
            .getAvailableApplications(false)
            .pipe(map(apps => apps.filter(app => app.id === appId).map(app => app.name)[0] || appId));
    }

}
