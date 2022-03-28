import { Component, EventEmitter, Input, OnInit, Output, TemplateRef } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ToastService } from '../../../shared/modules/toast/toast.service';
import { Topic, TopicRecord, TopicsService, TopicSubscription } from '../../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../../shared/services/environments.service';
import * as moment from 'moment';
import { map, take } from 'rxjs/operators';
import { ApplicationInfo, ApplicationsService } from '../../../shared/services/applications.service';

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

    selectedProducer: ApplicationInfo;

    producerApps: Observable<ApplicationInfo[]>;

    newTopicOwner: ApplicationInfo;

    constructor(
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private translateService: TranslateService,
        private toasts: ToastService,
        private modalService: NgbModal,
        private applicationsService: ApplicationsService
    ) {

    }

    ngOnInit() {
        this.producerApps = this.applicationsService.getAvailableApplications(false)
            .pipe(map(apps => apps.filter(app => this.topic.producers.includes(app.id))));
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
            .then(() => this.toasts.addSuccessToast('TOPIC_UPDATED_DESCRIPTION_SUCCESS'),
                err => this.toasts.addHttpErrorToast('TOPIC_UPDATED_DESCRIPTION_ERROR', err));
    }

    async handleUnDeprecationRequest() {
        return this.topicService
            .unDeprecateTopic(this.topic.name)
            .then(() => this.toasts.addSuccessToast('DELETE_DEPRECATION_MARK_SUCCESS'),
                err => this.toasts.addHttpErrorToast('DELETE_DEPRECATION_MARK_ERROR', err));
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
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment().pipe(take(1)));

        this.topicDataLoading = true;
        this.topicData = this.topicService
            .getTopicData(this.topic.name, environment.id)
            .catch(err => {
                this.toasts.addHttpErrorToast('Could not read topic data', err);
                return [];
            })
            .finally(() => {
                this.topicDataLoading = false;
            });
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg', scrollable: true });
    }

    async unsubscribe(subscription: TopicSubscription): Promise<any> {
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment().pipe(take(1)));

        return this.topicService.unsubscribeFromTopic(environment.id, subscription.clientApplication.id, subscription.id).then(
            () => {
                const msg =
                    subscription.state === 'PENDING'
                        ? 'DELETE_ABO_REQUEST'
                        : 'END_ABO_TOPIC';
                this.toasts.addSuccessToast(msg);
                this.subsChanged.emit();
            },
            err => this.toasts.addHttpErrorToast('DELETE_ABO_ERROR', err)
        );
    }

    async openRejectConfirmDlg(subscription: TopicSubscription, content: any) {
        this.selectedSubscription = subscription;
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    async openDeleteProducerDlg(producer: ApplicationInfo, content: any) {
        this.selectedProducer = producer;
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    openChangeOwnerDlg(producer: ApplicationInfo, content: TemplateRef<any>) {
        this.newTopicOwner = producer;
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    async changeTopicOwner(topicName: string, newOwnerId: string): Promise<any> {
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment().pipe(take(1)));
        return this.topicService.changeTopicOwner(environment.id, topicName, newOwnerId).then(
            () => {
                this.toasts.addSuccessToast('PRODUCER_PROMOTED_TOPIC_OWNER_SUCCESS');
            },
            err => this.toasts.addHttpErrorToast('PRODUCER_PROMOTED_TOPIC_OWNER_ERROR', err));
    }

    async deleteProducer(topicName: string, producerAppId: string): Promise<any> {
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment().pipe(take(1)));
        return this.topicService.deleteProducerFromTopic(environment.id, topicName, producerAppId).then(
            () => {
                this.toasts.addSuccessToast('PRODUCER_REMOVED_SUCCESS');
            },
            err => this.toasts.addHttpErrorToast('PRODUCER_REMOVED_ERROR', err));
    }

    private async updateSubscription(sub: TopicSubscription, approve: boolean, successMessage: string, errorMessage: string) {
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment().pipe(take(1)));

        return this.topicService.updateTopicSubscription(environment.id, this.topic.name, sub.id, approve).then(
            () => {
                this.toasts.addSuccessToast(successMessage);
                this.subsChanged.emit();
            },
            err => this.toasts.addHttpErrorToast(errorMessage, err)
        );
    }

}
