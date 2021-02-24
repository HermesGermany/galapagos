import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ToastService } from '../../../shared/modules/toast/toast.service';
import { Topic, TopicRecord, TopicsService, TopicSubscription } from '../../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../../shared/services/environments.service';
import * as moment from 'moment';
import { take } from 'rxjs/operators';

@Component({
    selector: 'app-topic-metadata-table',
    templateUrl: './topic-metadata-table.component.html',
    styleUrls: ['./topic-metadata-table.component.scss']
})
export class TopicMetadataTableComponent implements OnInit {

    @Input() topic: Topic;

    @Input() isOwnerOfTopic: boolean;

    @Input() topicName: string;

    @Input() topicSubscribers: TopicSubscription[];

    @Input() approvedTopicSubscribers: TopicSubscription[];

    @Input() pendingTopicSubscribers: TopicSubscription[];

    @Output() subsChanged = new EventEmitter();

    selectedEnvironment: Observable<KafkaEnvironment>;

    updatedTopicDescription: string;

    topicDataLoading = false;

    topicData: Promise<TopicRecord[]>;

    loadingSubscribers: boolean;

    selectedSubscription: TopicSubscription;

    constructor(
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private translateService: TranslateService,
        private toasts: ToastService,
        private modalService: NgbModal
    ) {

    }

    ngOnInit(): void {
        this.selectedEnvironment = this.environmentsService.getCurrentEnvironment();
    }

    async openChangeDescDlg(content: any) {
        this.updatedTopicDescription = this.topic.description;
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    async rejectSubscription(sub: TopicSubscription) {
        return this.updateSubscription(
            sub,
            false,
            'Der Anwendung wurden die Rechte für dieses Topic entzogen.',
            'Der Anwendung konnten die Rechte für dieses Topic nicht entzogen werden'
        );
    }

    async updateTopicDesc() {
        return this.topicService
            .updateTopicDescription(this.updatedTopicDescription, this.topic.name)
            .then(() => this.toasts.addSuccessToast('Beschreibung erfolgreich geändert'),
                err => this.toasts.addHttpErrorToast('Beschreibung konnte nicht geändert werden', err));
    }

    async handleUnDeprecationRequest() {
        return this.topicService
            .unDeprecateTopic(this.topic.name)
            .then(() => this.toasts.addSuccessToast('Deprecation-Markierung erfolgreich entfernt'),
                err => this.toasts.addHttpErrorToast('Deprecation-Markierung konnte nicht entfernt werden', err));
    }

    getEolDateForCurrentLang(eolDate) {
        if (eolDate) {
            return moment(eolDate).locale(this.translateService.currentLang).format('L');
        }

        return '';
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

    async approveSubscription(sub: TopicSubscription) {
        return this.updateSubscription(
            sub,
            true,
            'Die Anwendung wurde erfolgreich für dieses Topic freigegeben.',
            'Die Anwendung konnte nicht für dieses Topic freigegeben werden'
        );
    }

    async openDataDlg(content: any) {
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        this.topicDataLoading = true;
        this.topicData = this.topicService
            .getTopicData(this.topic.name, environment.id)
            .catch(err => {
                this.toasts.addHttpErrorToast(this.translateService.instant('Could not read topic data'), err);
                return [];
            })
            .finally(() => {
                this.topicDataLoading = false;
            });

        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg', scrollable: true });
    }

    async unsubscribe(subscription: TopicSubscription): Promise<any> {
        // TODO confirmation box!
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.unsubscribeFromTopic(environment.id, subscription.clientApplication.id, subscription.id).then(
            () => {
                const msg =
                    subscription.state === 'PENDING'
                        ? 'Die Abonnements-Anfrage wurde gelöscht.'
                        : 'Die Anwendung abonniert dieses Topic nicht länger. ACHTUNG: Die Leserechte wurden entzogen!';
                this.toasts.addSuccessToast(msg);
                this.subsChanged.emit();
            },
            err => this.toasts.addHttpErrorToast('Das Abonnement konnte nicht gelöscht werden', err)
        );
    }

    async openRejectConfirmDlg(subscription: TopicSubscription, content: any) {
        this.selectedSubscription = subscription;
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }


    private async updateSubscription(sub: TopicSubscription, approve: boolean, successMessage: string, errorMessage: string) {
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.updateTopicSubscription(environment.id, this.topic.name, sub.id, approve).then(
            () => {
                this.toasts.addSuccessToast(successMessage);
                this.subsChanged.emit();
            },
            err => this.toasts.addHttpErrorToast(errorMessage, err)
        );
    }

}
