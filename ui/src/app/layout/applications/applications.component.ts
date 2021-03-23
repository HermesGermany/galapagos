import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { NgbModal, NgbNavChangeEvent } from '@ng-bootstrap/ng-bootstrap';
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
import { ApplicationCertificate, CertificateService } from '../../shared/services/certificates.service';
import { TopicsService } from '../../shared/services/topics.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { OpenCertificateDialogEvent } from './application-block.component';

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

    submitting = false;

    appListLoading = true;

    showCopyCompleteText = false;

    currentLang: Observable<string>;

    activeTab: string;

    certificateCreationType = 'hasCsr';

    constructor(
        private modalService: NgbModal,
        private applicationsService: ApplicationsService,
        private certificateService: CertificateService,
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

        this.activeTab = 'new';

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

    handleDlgTabChange(event: NgbNavChangeEvent) {
        this.activeTab = event.nextId;
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
