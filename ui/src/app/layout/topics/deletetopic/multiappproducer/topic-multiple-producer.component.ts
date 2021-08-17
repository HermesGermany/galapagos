import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Topic, TopicsService } from '../../../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../../../shared/services/environments.service';
import { ToastService } from '../../../../shared/modules/toast/toast.service';
import { ApiKeyService } from '../../../../shared/services/apikey.service';
import { Observable } from 'rxjs';
import { ApplicationInfo, ApplicationsService } from '../../../../shared/services/applications.service';

@Component({
    selector: 'app-topic-multiple-producer',
    templateUrl: './topic-multiple-producer.component.html',
    styleUrls: ['./topic-multiple-producer.component.scss']
})
export class TopicMultipleProducerComponent implements OnInit {
    @Input() topic: Topic;

    @Input() selectedEnvironment: KafkaEnvironment;

    @Output() closeModal = new EventEmitter();

    showRegistrationWarning = false;

    selectedProducer: ApplicationInfo;

    userApplications: Observable<ApplicationInfo[]>;

    producerApps: string[] = [];

    constructor(
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private toasts: ToastService,
        private apiKeyService: ApiKeyService,
        private applicationsService: ApplicationsService
    ) {

    }

    ngOnInit() {
        const topicOwnerAppId = this.topic.ownerApplication ? this.topic.ownerApplication.id : null;
        this.userApplications = this.applicationsService
            .getAvailableApplicationMetadata(this.selectedEnvironment.id, topicOwnerAppId, this.topic.name);
    }

    addProducer(selectedProducer: ApplicationInfo): Promise<any> {
        this.selectedProducer = selectedProducer;
        this.producerApps.push(this.selectedProducer.id);
        return Promise.resolve();

    }

    submitSelectedProducer(): Promise<any> {
        if (!this.selectedProducer) {
            this.toasts.addErrorToast('Bitte wähle zunächst eine Producer Anwendung aus.');
            return Promise.resolve();
        }
        return this.topicService.addProducersToTopic(this.producerApps, this.selectedEnvironment.id, this.topic.name).then(
            () => {
                this.toasts.addSuccessToast('Producer wurde erfolgreich hinzugefügt.');
                this.closeModal.emit();
            },
            err => this.toasts.addHttpErrorToast('Producer konnte nicht hinzugefügt werden.', err))
            .finally(() => this.selectedProducer = null);
    }

}
