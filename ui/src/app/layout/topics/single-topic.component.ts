import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { ActivatedRoute, Router } from '@angular/router';
import { Topic, TopicRecord, TopicsService, TopicSubscription } from '../../shared/services/topics.service';
import { combineLatest, Observable } from 'rxjs';
import { finalize, map, mergeMap, shareReplay, startWith, take } from 'rxjs/operators';
import { ApplicationsService, UserApplicationInfo } from '../../shared/services/applications.service';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbDateStruct, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CertificateService } from '../../shared/services/certificates.service';
import * as moment from 'moment';
import { ServerInfoService } from '../../shared/services/serverinfo.service';
import { SchemaSectionComponent } from './schema-section.component';

@Component({
    selector: 'app-single-topic',
    templateUrl: './single-topic.component.html',
    styleUrls: ['./single-topic.component.scss'],
    animations: [routerTransition()],
    encapsulation: ViewEncapsulation.None
})
export class SingleTopicComponent implements OnInit {

    topic: Observable<Topic>;

    topicName: Observable<string>;

    loading: Observable<boolean>;

    topicSubscribers: Observable<TopicSubscription[]>;

    approvedTopicSubscribers: Observable<TopicSubscription[]>;

    pendingTopicSubscribers: Observable<TopicSubscription[]>;

    loadingSubscribers: boolean;

    availableApplications: Observable<UserApplicationInfo[]>;

    loadingApplications: Observable<boolean>;

    selectedApplication: UserApplicationInfo;

    subscriptionDescription: string;

    selectedEnvironment: Observable<KafkaEnvironment>;

    translateParams: any = {};

    isOwnerOfTopic: Observable<boolean>;

    topicNameConfirmText = '';

    topicDataLoading = false;

    topicData: Promise<TopicRecord[]>;

    selectedSubscription: TopicSubscription;

    showRegistrationWarning = false;

    deprecatedDescription: string;

    eolDate: NgbDateStruct;

    deprecateTopicHtml: Observable<string>;

    minDeprecationDate: Observable<{ year: number; month: number; day: number }>;

    updatedTopicDescription: string;

    constructor(
        private route: ActivatedRoute,
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private applicationsService: ApplicationsService,
        private certificateService: CertificateService,
        private serverInfoService: ServerInfoService,
        private toasts: ToastService,
        private router: Router,
        private translateService: TranslateService,
        private modalService: NgbModal,
        private schemaSection: SchemaSectionComponent
    ) {

        route.queryParamMap.subscribe({
            next: params => {
                if (params.has('environment')) {
                    const envId = params.get('environment');
                    environmentsService
                        .getEnvironments()
                        .pipe(take(1))
                        .toPromise()
                        .then(envs => {
                            const env = envs.find(e => e.id === envId);
                            if (env) {
                                environmentsService.setCurrentEnvironment(env);
                            }
                        });
                }
            }
        });
    }

    ngOnInit() {
        this.topicName = this.route.params.pipe(map(params => params['name'] as string)).pipe(shareReplay(1));
        this.selectedEnvironment = this.environmentsService.getCurrentEnvironment();

        const listTopics = this.topicService.listTopics();

        this.loading = listTopics.getLoadingStatus();

        this.topic = combineLatest([this.topicName, listTopics.getObservable()])
            .pipe(map(values => values[1].find(t => t.name === values[0])))
            .pipe(shareReplay(1));

        combineLatest([this.topic, this.environmentsService.getCurrentEnvironment()]).subscribe({
            next: value => {
                if (value[0]) {
                    this.loadSubscribers(value[0], value[1].id);
                    this.translateParams.topicName = value[0].name;
                }
            }
        });

        this.isOwnerOfTopic = combineLatest([this.topic, this.applicationsService.getUserApplications().getObservable()]).pipe(
            map(value => value[0] && value[1] && !!value[1].find(app => value[0].ownerApplication.id === app.id))
        );

        this.environmentsService.getCurrentEnvironment().subscribe({ next: env => (this.translateParams.environmentName = env.name) });


        // another nice Observable construct :-)
        // React on each language change to recalculate text, as locale also influences moment's text calculation.
        // As onLangChange only emits on a CHANGE of the language, we start it with the current language (I really hate that)
        const currentLang = this.translateService.onLangChange.pipe(map(evt => evt.lang))
            .pipe(startWith(this.translateService.currentLang));
        this.deprecateTopicHtml = combineLatest([currentLang, this.serverInfoService.getUiConfig()])
            .pipe(mergeMap(val => this.translateService.get('DEPRECATE_TOPIC_HTML',
                { period: this.toPeriodText(val[1].minDeprecationTime) }).pipe(map(o => o.toString()))
            ));
        this.minDeprecationDate = this.serverInfoService.getUiConfig()
            .pipe(map(config => this.getValidDatesDeprecation(config.minDeprecationTime)));
    }

    getValidDatesDeprecation(date: { years: number; months: number; days: number }) {
        const minDeprecationTime = moment().add(date.years, 'y')
            .add(date.months, 'month')
            .add(date.days, 'days').locale(this.translateService.currentLang);

        return {
            year: +minDeprecationTime.format('YYYY'),
            month: +minDeprecationTime.format('M'),
            day: +minDeprecationTime.format('D')
        };
    }

    getEolDateForCurrentLang(eolDate) {
        if (eolDate) {
            return moment(eolDate).locale(this.translateService.currentLang).format('L');
        }

        return '';
    }

    async handleDeprecationRequest() {
        const date = this.eolDate;
        const localDate = moment().year(date.year).month(date.month - 1).date(date.day).utc(true).format('YYYY-MM-DD');
        const topic = await this.topic.pipe(take(1)).toPromise();
        return this.topicService
            .deprecateTopic(this.deprecatedDescription, localDate, topic.name)
            .then(() => this.toasts.addSuccessToast('Das Topic wurde erfolgreich als deprecated markiert'),
                err => this.toasts.addHttpErrorToast('Das Topic konnte nicht als deprecated markiert werden', err));
    }

    async handleUnDeprecationRequest() {
        const topic = await this.topic.pipe(take(1)).toPromise();
        return this.topicService
            .unDeprecateTopic(topic.name)
            .then(() => this.toasts.addSuccessToast('Deprecation-Markierung erfolgreich entfernt'),
                err => this.toasts.addHttpErrorToast('Deprecation-Markierung konnte nicht entfernt werden', err));
    }

    async updateTopicDesc() {
        const topic = await this.topic.pipe(take(1)).toPromise();
        return this.topicService
            .updateTopicDescription(this.updatedTopicDescription, topic.name)
            .then(() => this.toasts.addSuccessToast('Beschreibung erfolgreich geändert'),
                err => this.toasts.addHttpErrorToast('Beschreibung konnte nicht geändert werden', err));
    }

    async subscribeToTopic(): Promise<any> {
        if (!this.selectedApplication) {
            return Promise.resolve();
        }

        const topic = await this.topic.pipe(take(1)).toPromise();
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.subscribeToTopic(topic.name, environment.id, this.selectedApplication.id, this.subscriptionDescription)
            .then(() => {
                if (topic.subscriptionApprovalRequired) {
                    this.toasts.addSuccessToast('Die Topic-Owner wurden über die Abonnement-Anfrage informiert');
                } else {
                    this.toasts.addSuccessToast('Die Anwendung hat das Topic nun abonniert');
                }
                this.loadSubscribers(topic, environment.id);
            },
            err => this.toasts.addHttpErrorToast('Das Abonnement konnte nicht erstellt werden', err)
            )
            .finally(() => this.subscriptionDescription = null);
    }

    async unsubscribe(subscription: TopicSubscription): Promise<any> {
        // TODO confirmation box!
        const topic = await this.topic.pipe(take(1)).toPromise();
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.unsubscribeFromTopic(environment.id, subscription.clientApplication.id, subscription.id).then(
            () => {
                const msg =
                    subscription.state === 'PENDING'
                        ? 'Die Abonnements-Anfrage wurde gelöscht.'
                        : 'Die Anwendung abonniert dieses Topic nicht länger. ACHTUNG: Die Leserechte wurden entzogen!';
                this.toasts.addSuccessToast(msg);
                this.loadSubscribers(topic, environment.id);
            },
            err => this.toasts.addHttpErrorToast('Das Abonnement konnte nicht gelöscht werden', err)
        );
    }

    async approveSubscription(sub: TopicSubscription) {
        return this.updateSubscription(
            sub,
            true,
            'Die Anwendung wurde erfolgreich für dieses Topic freigegeben.',
            'Die Anwendung konnte nicht für dieses Topic freigegeben werden'
        );
    }

    async rejectSubscription(sub: TopicSubscription) {
        return this.updateSubscription(
            sub,
            false,
            'Der Anwendung wurden die Rechte für dieses Topic entzogen.',
            'Der Anwendung konnten die Rechte für dieses Topic nicht entzogen werden'
        );
    }

    openDeleteConfirmDlg(content: any) {
        this.topicNameConfirmText = '';
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    async openChangeDescDlg(content: any) {
        const topic = await this.topic.pipe(take(1)).toPromise();
        this.updatedTopicDescription = topic.description;
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    async openRejectConfirmDlg(subscription: TopicSubscription, content: any) {
        this.selectedSubscription = subscription;
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    async deleteTopic(): Promise<any> {
        const topic = await this.topic.pipe(take(1)).toPromise();
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.deleteTopic(environment.id, topic.name).then(
            () => {
                this.toasts.addSuccessToast('Das Topic wurde gelöscht.');
                this.router.navigateByUrl('/topics');
            },
            err => this.toasts.addHttpErrorToast('Das Topic konnte nicht gelöscht werden', err)
        );
    }

    async openDataDlg(content: any) {
        const topic = await this.topic.pipe(take(1)).toPromise();
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        this.topicDataLoading = true;
        this.topicData = this.topicService
            .getTopicData(topic.name, environment.id)
            .catch(err => {
                this.toasts.addHttpErrorToast(this.translateService.instant('Could not read topic data'), err);
                return [];
            })
            .finally(() => {
                this.topicDataLoading = false;
            });

        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg', scrollable: true });
    }

    formatDataValues() {
        const tryFormatValue = (rec: TopicRecord) => {
            const val = rec.value;
            try {
                const obj = JSON.parse(val);
                return {
                    partition: rec.partition,
                    offset: rec.offset,
                    key: rec.key,
                    value: JSON.stringify(obj, null, 4)
                };
            } catch (e) {
                return rec;
            }
        };

        this.topicData = this.topicData.then(data => data.map(rec => tryFormatValue(rec)));

        return false;
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

    private async updateSubscription(sub: TopicSubscription, approve: boolean, successMessage: string, errorMessage: string) {
        const topic = await this.topic.pipe(take(1)).toPromise();
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.updateTopicSubscription(environment.id, topic.name, sub.id, approve).then(
            () => {
                this.toasts.addSuccessToast(successMessage);
                this.loadSubscribers(topic, environment.id);
            },
            err => this.toasts.addHttpErrorToast(errorMessage, err)
        );
    }

    private loadSubscribers(topic: Topic, environmentId: string) {
        this.loadingSubscribers = true;
        this.topicSubscribers = this.topicService
            .getTopicSubscribers(topic.name, environmentId)
            .pipe(shareReplay(1))
            .pipe(map(subs => subs.filter(s => s.state !== 'REJECTED')))
            .pipe(finalize(() => (this.loadingSubscribers = false)));

        this.pendingTopicSubscribers = this.topicSubscribers.pipe(map(subs => subs.filter(sub => sub.state === 'PENDING')));
        this.approvedTopicSubscribers = this.topicSubscribers.pipe(map(subs => subs.filter(sub => sub.state === 'APPROVED')));

        const ownerAppId = topic.ownerApplication ? topic.ownerApplication.id : null;

        this.topicSubscribers
            .pipe(take(1))
            .toPromise()
            .then(subs => {
                this.loadingApplications = this.applicationsService.getUserApplications().getLoadingStatus();
                this.availableApplications = this.applicationsService
                    .getUserApplications()
                    .getObservable()
                    .pipe(
                        map(apps =>
                            apps.filter(
                                app =>
                                    (!ownerAppId || app.id !== ownerAppId) &&
                                    !subs.find(sub => sub.clientApplication && sub.clientApplication.id === app.id)
                            )
                        )
                    );
            });
    }

    private toPeriodText(period: { years: number; months: number; days: number }): string {
        const target = moment().add(period.years, 'y').add(period.months, 'month').add(period.days, 'days');
        const oldThreshold = moment.relativeTimeThreshold('d') as number;

        // special treatment: If days set, avoid moment "rounding" to months
        // Note: this still produces wrong results for some values of "days"
        // moment.js should be replaced with a better library.
        if (period.days) {
            moment.relativeTimeThreshold('d', 99999);
        }

        const result = moment().locale(this.translateService.currentLang).to(target, true);
        moment.relativeTimeThreshold('d', oldThreshold);
        return result;
    }

}
