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

import * as moment from 'moment';
import { map, mergeMap, shareReplay, startWith } from 'rxjs/operators';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TopicsService } from '../../shared/services/topics.service';
import { TranslateService } from '@ngx-translate/core';
import { OpenApiKeyDialogEvent, OpenCertificateDialogEvent } from './application-block.component';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiKeyService } from '../../shared/services/apikey.service';
import { ApplicationCertificate, CertificateService } from '../../shared/services/certificates.service';

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

        this.activeTab = 'new';

        this.currentEnv = this.environmentsService.getCurrentEnvironment();

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
            return this.apiKeyService
                .requestApiKey(appId, envId.id)
                .then(
                    apiKey => {
                        this.key = apiKey.apiKey;
                        this.secret = apiKey.apiSecret;
                        this.showApiKeyTable = true;
                        this.toasts.addSuccessToast('API Key erfolgreich erstellt');
                    },
                    (err: HttpErrorResponse) => {
                        this.apiKeyRequestError = err;
                        this.toasts.addHttpErrorToast('API Key konnte nicht erstellt werden', err);
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
                    () => this.toasts.addSuccessToast('Zertifikat erfolgreich erstellt (bitte Browser-Downloads beachten)'),
                    (err: HttpErrorResponse) => this.toasts.addHttpErrorToast('Zertifikat konnte nicht erstellt werden', err)
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
                const now = moment();
                const expiryWarnLevel = moment(isoExpiryDate).isBefore(now) ? 'danger' :
                    (moment(isoExpiryDate).diff(now, 'days') < 90 ? 'warning' : 'info');

                this.certificateDlgData.expiryWarningType = expiryWarnLevel;
                this.certificateDlgData.expiryWarningHtml = this.currentLang.pipe(mergeMap(lang =>
                    this.translateService.get(expiryWarnLevel === 'danger' ? 'CERTIFICATE_EXPIRED_HTML' : 'CERTIFICATE_EXPIRY_HTML',
                        { expiryDate: moment(isoExpiryDate).locale(lang).format('L') })));
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

    extractCommonName(dn: string): string {
        return dn.match(/CN=([^,]+)/)[1];
    }

    extractOrgUnitName(dn: string): string {
        return dn.match(/OU=([^,]+)/)[1];
    }

}
