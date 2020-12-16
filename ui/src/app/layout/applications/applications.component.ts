import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { NgbModal, NgbTabChangeEvent } from '@ng-bootstrap/ng-bootstrap';
import {
    ApplicationInfo,
    ApplicationOwnerRequest,
    ApplicationsService,
    UserApplicationInfo
} from '../../shared/services/applications.service';
import { combineLatest, Observable, of } from 'rxjs';
import { toNiceTimestamp } from '../../shared/util/time-util';

import * as moment from 'moment';
import { flatMap, map, shareReplay, startWith } from 'rxjs/operators';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { ApplicationCertificate, CertificateService } from '../../shared/services/certificates.service';
import { TopicsService } from '../../shared/services/topics.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';

interface UserApplicationInfoWithTopics extends UserApplicationInfo {

    owningTopics: string[];

    usingTopics: Observable<string[]>;

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
        existingCertificate: <ApplicationCertificate>null,
        csrData: null,
        topicPrefixes: [],
        selectedTopicPrefix: null,
        groupPrefixes: [],
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
                const getOwningTopicsForApp = (appId: string): string[] => {
                    return topics.filter(topic => topic.ownerApplication.id === appId && topic.topicType !== 'INTERNAL').map(t => t.name);
                };

                return <UserApplicationInfoWithTopics[]>apps.map(app => ({
                    ...app, owningTopics: getOwningTopicsForApp(app.id),
                    usingTopics: this.applicationsService.getApplicationSubscriptions(app.id, env.id)
                        .pipe(map(subs => subs.map(s => s.topicName)))
                }));
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

    openCertDlg(app: UserApplicationInfo, env: KafkaEnvironment, content: any) {
        const names = [this.cnForApp(app.name).toLowerCase().replace(/_/g, '-')];
        app.aliases.forEach(a => names.push(this.cnForApp(a).toLowerCase().replace(/_/g, '-')));

        const topicPrefixes = names.map(n => n + '.internal.');
        const groupPrefixes = names.map(n => n + '.');

        this.certificateDlgData = {
            applicationId: app.id,
            applicationName: app.name,
            environment: env,
            existingCertificate: null,
            csrData: '',
            topicPrefixes: topicPrefixes,
            selectedTopicPrefix: topicPrefixes[0],
            groupPrefixes: groupPrefixes.map(n => 'de.hlg.' + n),
            commonName: this.cnForApp(app.name),
            orgUnitName: null,
            keyfileName: this.cnForApp(app.name) + '_' + env.id + '.key',
            expiryWarningType: 'info',
            expiryWarningHtml: of('')
        };
        this.certificateService.getApplicationCertificates(app.id).then(certs => {
            this.certificateDlgData.existingCertificate = certs.find(cert => cert.environmentId === env.id) || null;
            if (this.certificateDlgData.existingCertificate) {
                this.certificateDlgData.commonName = this.extractCommonName(this.certificateDlgData.existingCertificate.dn);
                this.certificateDlgData.orgUnitName = this.extractOrgUnitName(this.certificateDlgData.existingCertificate.dn);
                this.certificateDlgData.keyfileName = this.certificateDlgData.commonName + '_' + env.id + '.key';

                const isoExpiryDate = this.certificateDlgData.existingCertificate.expiresAt;
                const now = moment();
                const expiryWarnLevel = moment(isoExpiryDate).isBefore(now) ? 'danger' :
                    (moment(isoExpiryDate).diff(now, 'days') < 90 ? 'warning' : 'info');

                this.certificateDlgData.expiryWarningType = expiryWarnLevel;
                this.certificateDlgData.expiryWarningHtml = this.currentLang.pipe(flatMap(lang =>
                    this.translateService.get(expiryWarnLevel === 'danger' ? 'CERTIFICATE_EXPIRED_HTML' : 'CERTIFICATE_EXPIRY_HTML',
                        { expiryDate: moment(isoExpiryDate).locale(lang).format('L') })));
            }
            this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg', windowClass: 'modal-xxl' });
        });
    }

    handleDlgTabChange(event: NgbTabChangeEvent) {
        this.activeTab = event.nextId;
    }

    generateCertificate(): void {
        if (this.certificateDlgData.applicationId && this.certificateDlgData.environment) {
            this.certificateService
                .requestAndDownloadApplicationCertificate(
                    this.certificateDlgData.applicationId,
                    this.certificateDlgData.environment.id,
                    this.certificateDlgData.csrData,
                    this.certificateDlgData.selectedTopicPrefix,
                    this.activeTab === 'extend'
                )
                .then(
                    () => this.toasts.addSuccessToast('Zertifikat erfolgreich erstellt (bitte Browser-Downloads beachten)'),
                    (err: HttpErrorResponse) => this.toasts.addHttpErrorToast('Zertifikat konnte nicht erstellt werden', err)
                );
        }
    }

    cnForApp(app: string) {
        if (!app) {
            return '';
        }
        let cn = app.toLowerCase();
        cn = cn.replace(/[^0-9a-zA-Z]/g, '_');
        while (cn.indexOf('__') > -1) {
            cn = cn.replace('__', '_');
        }
        if (cn.startsWith('_')) {
            cn = cn.substring(1);
        }
        if (cn.endsWith('_')) {
            cn = cn.substring(0, cn.length - 1);
        }
        return cn;
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
