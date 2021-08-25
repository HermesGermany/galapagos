import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Topic, TopicsService } from '../../../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../../../shared/services/environments.service';
import { ToastService } from '../../../../shared/modules/toast/toast.service';
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

    selectableProducerApps: ApplicationInfo[];

    constructor(
        private applicationsService: ApplicationsService,
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private toasts: ToastService
    ) {

    }

    ngOnInit() {
        const topicOwnerAppId = this.topic.ownerApplication ? this.topic.ownerApplication.id : null;
        this.applicationsService.getRegisteredApplications(this.selectedEnvironment.id).then(
            registeredApps => {
                this.selectableProducerApps = registeredApps.filter(producerApp => producerApp.id !== topicOwnerAppId
                    && !this.topic.producers.includes(producerApp.id));
            });
    }

    submitSelectedProducer(): Promise<any> {
        if (!this.selectedProducer) {
            this.toasts.addErrorToast('Bitte wähle zunächst eine Producer Anwendung aus.');
            return Promise.resolve();
        }
        return this.topicService.addProducerToTopic(this.selectedProducer.id, this.selectedEnvironment.id, this.topic.name).then(
            () => {
                this.toasts.addSuccessToast('Producer wurde erfolgreich hinzugefügt.');
                this.closeModal.emit();
            },
            err => this.toasts.addHttpErrorToast('Producer konnte nicht hinzugefügt werden.', err))
            .finally(() => this.selectedProducer = null);
    }

}
