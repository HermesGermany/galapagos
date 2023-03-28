import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { ModalDismissReasons, NgbModal, NgbNavChangeEvent } from '@ng-bootstrap/ng-bootstrap';
import {
    ApplicationInfo,
    ApplicationOwnerRequest,
    ApplicationPrefixes,
    ApplicationsService,
    UserApplicationInfo
} from '../../shared/services/applications.service';
import { combineLatest, Observable, of } from 'rxjs';
import { toNiceTimestamp } from '../../shared/util/time-util';

import { DateTime } from 'luxon';
import { map, mergeMap, shareReplay, startWith } from 'rxjs/operators';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TopicsService } from '../../shared/services/topics.service';
import { TranslateService } from '@ngx-translate/core';
import { OpenApiKeyDialogEvent, OpenCertificateDialogEvent } from './application-block.component';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiKeyService } from '../../shared/services/apikey.service';
import { ApplicationCertificate, CertificateService } from '../../shared/services/certificates.service';
import { copy } from '../../shared/util/copy-util';

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

    apiKeyRequestError: any;

    currentEnv: Observable<KafkaEnvironment>;

    certificateDlgData = {
        applicationName: null,
        applicationId: null,
        environment: null,
        existingCertificate: null as ApplicationCertificate,
        csrData: null,
        commonName: null,
        orgUnitName: null,
        keyfileName: null,
        expiryWarningType: 'info',
        expiryWarningHtml: of('')
    };

    activeTab: string;

    certificateCreationType = 'hasCsr';

    constructor(
        private modalService: NgbModal,
        private applicationsService: ApplicationsService,
        private apiKeyService: ApiKeyService,
        private environmentsService: EnvironmentsService,
        private topicsService: TopicsService,
        private toasts: ToastService,
        private translateService: TranslateService,
        private certificateService: CertificateService
    ) {
    }

    ngOnInit() {
        const now = DateTime.now();

        this.currentLang = this.translateService.onLangChange.pipe(map(evt => evt.lang))
            .pipe(startWith(this.translateService.currentLang)).pipe(shareReplay(1));

        const relevant = (req: ApplicationOwnerRequest) => DateTime.fromISO(req.lastStatusChangeAt)
            .minus(30, 'days') < now;
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

        this.activeTab = 'new';

        this.currentEnv = this.environmentsService.getCurrentEnvironment();

        this.applicationsService.refresh().then();
    }

    submitRequest(): Promise<any> {
        if (!this.selectedApplicationForRequest) {
            this.toasts.addErrorToast('APPLICATION_SELECTION_EMPTY');
            return Promise.resolve();
        }

        this.submitting = true;
        return this.applicationsService
            .submitApplicationOwnerRequest(this.selectedApplicationForRequest.id, this.commentsForRequest)
            .then(
                () => {
                    this.selectedApplicationForRequest = null;
                    this.commentsForRequest = '';
                    this.toasts.addSuccessToast('REQUEST_CREATION_SUCCESS');
                },
                err => this.toasts.addHttpErrorToast('REQUEST_CREATION_ERROR', err)
            )
            .finally(() => (this.submitting = false));
    }

    async cancelRequest(req: ApplicationOwnerRequest): Promise<any> {
        return this.applicationsService.cancelApplicationOwnerRequest(req.id).then(
            () => this.toasts.addSuccessToast('REQUEST_ABORT_SUCCESS'),
            err => this.toasts.addHttpErrorToast('REQUEST_ABORT_ERROR', err)
        );
    }

    async openApiKeyDlg(event: OpenApiKeyDialogEvent, content: any) {
        const app = event.application;
        const env = event.environment;

        this.apiKeyDlgData = {
            applicationId: app.id,
            applicationName: app.name,
            environment: env,
            existingApiKey: null
        };
        const apiKey = await this.apiKeyService.getApplicationApiKeysPromise(app.id);
        this.apiKeyDlgData.existingApiKey = apiKey.authentications[env.id];

        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg', windowClass: 'modal-xxl' }).result.then(result => {
        }, reason => {
            if (reason === ModalDismissReasons.BACKDROP_CLICK || reason === ModalDismissReasons.ESC) {
                this.handleDlgDismiss();
            }
        });
    }

    generateApiKey(): Promise<any> {
        if (this.apiKeyDlgData.applicationId && this.apiKeyDlgData.environment) {
            const appId = this.apiKeyDlgData.applicationId;
            const envId = this.apiKeyDlgData.environment;
            this.copiedKey = false;
            this.copiedSecret = false;
            return this.apiKeyService
                .requestApiKey(appId, envId.id)
                .then(
                    apiKey => {
                        this.key = apiKey.apiKey;
                        this.secret = apiKey.apiSecret;
                        this.showApiKeyTable = true;
                        this.toasts.addSuccessToast('API_KEY_CREATION_SUCCESS');
                    },
                    (err: HttpErrorResponse) => {
                        this.apiKeyRequestError = err.message;
                        this.toasts.addHttpErrorToast('API_KEY_CREATION_ERROR', err);
                    }
                )
                .then(() => this.apiKeyService.getApplicationApiKeys(appId).refresh());
        }
    }

    generateCertificate(): void {
        if (this.certificateDlgData.applicationId && this.certificateDlgData.environment) {
            const appId = this.certificateDlgData.applicationId;
            this.certificateService
                .requestAndDownloadApplicationCertificate(
                    appId,
                    this.certificateDlgData.environment.id,
                    this.certificateDlgData.csrData,
                    this.activeTab === 'extend'
                )
                .then(
                    () => this.toasts.addSuccessToast('CERTIFICATE_CREATION_SUCCESS'),
                    (err: HttpErrorResponse) => this.toasts.addHttpErrorToast('CERTIFICATE_CREATION_ERROR', err)
                )
                .then(() => this.certificateService.getApplicationCertificates(appId).refresh());
        }
    }

    handleDlgTabChange(event: NgbNavChangeEvent) {
        this.activeTab = event.nextId;
    }

    openCertDlg(event: OpenCertificateDialogEvent, content: any) {
        const app = event.application;
        const env = event.environment;

        this.certificateDlgData = {
            applicationId: app.id,
            applicationName: app.name,
            environment: env,
            existingCertificate: null,
            csrData: '',
            commonName: null,
            orgUnitName: null,
            keyfileName: null,
            expiryWarningType: 'info',
            expiryWarningHtml: of('')
        };
        this.certificateService.getApplicationCertificatesPromise(app.id).then(certs => {
            this.certificateDlgData.existingCertificate = certs.find(cert => cert.environmentId === env.id) || null;
            if (this.certificateDlgData.existingCertificate) {
                const dn = this.certificateDlgData.existingCertificate.dn;
                this.certificateDlgData.commonName = this.extractCommonName(dn);
                this.certificateDlgData.orgUnitName = this.extractOrgUnitName(dn);
                this.certificateDlgData.keyfileName = this.certificateDlgData.commonName + '_' + env.id + '.key';

                const isoExpiryDate = this.certificateDlgData.existingCertificate.expiresAt;
                const now = DateTime.now();
                const expiryWarnLevel = DateTime.fromISO(isoExpiryDate) < now ? 'danger' :
                    (DateTime.fromISO(isoExpiryDate).diff(now, 'days').toObject().days < 90 ? 'warning' : 'info');

                this.certificateDlgData.expiryWarningType = expiryWarnLevel;
                this.certificateDlgData.expiryWarningHtml = this.currentLang.pipe(mergeMap(lang =>
                    this.translateService.get(expiryWarnLevel === 'danger' ? 'CERTIFICATE_EXPIRED_HTML' : 'CERTIFICATE_EXPIRY_HTML',
                        { expiryDate: DateTime.fromISO(isoExpiryDate).setLocale(lang).toFormat('L') })));
            } else {
                this.certificateDlgData.commonName = 'app';
                this.certificateDlgData.keyfileName = 'app.key';
                this.certificateService.getApplicationCn(app.id).then(cn => {
                    this.certificateDlgData.commonName = cn;
                    this.certificateDlgData.keyfileName = this.certificateDlgData.commonName + '_' + env.id + '.key';
                });
            }
            this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg', windowClass: 'modal-xxl' });
        });
    }

    handleDlgDismiss(): void {
        this.showApiKeyTable = false;
        this.secret = null;
        this.apiKeyRequestError = null;
    }

    copyValue(value: string) {
        copy(value);
        if (value === this.key) {
            this.copiedKey = true;
        } else {
            this.copiedSecret = true;
        }
    }

    niceTimestamp(str: string): Observable<string> {
        return toNiceTimestamp(str, this.translateService);
    }

    toAppName(appId: string): Observable<string> {
        // this is not as bad as it looks! ApplicationService returns a ReplaySubject
        // and does not issue a new AJAX request for every call here...
        return this.applicationsService
            .getAvailableApplications(false)
            .pipe(map(apps => apps.filter(app => app.id === appId).map(app => app.name)[0] || appId));
    }

    extractCommonName(dn: string): string {
        return dn.match(/CN=([^,]+)/)[1];
    }

    extractOrgUnitName(dn: string): string {
        return dn.match(/OU=([^,]+)/)[1];
    }

}
