import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Topic, TopicsService } from '../../../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../../../shared/services/environments.service';
import { ToastService } from '../../../../shared/modules/toast/toast.service';
import { ApplicationInfo } from '../../../../shared/services/applications.service';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

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

    selectedProducer: ApplicationInfo = null;

    selectableProducerApps: Observable<ApplicationInfo[]>;

    constructor(
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private toasts: ToastService
    ) {

    }

    ngOnInit() {
        const topicOwnerAppId = this.topic.ownerApplication ? this.topic.ownerApplication.id : null;
        this.selectableProducerApps = this.topicService.getRegisteredApplications(this.selectedEnvironment.id).pipe(
            map(producerApps => producerApps.filter(producerApp => producerApp.id !== topicOwnerAppId
                && !this.topic.producers.includes(producerApp.id))));
    }

    submitSelectedProducer(): Promise<any> {
        if (!this.selectedProducer) {
            this.toasts.addErrorToast('Bitte wähle zunächst eine Producer Anwendung aus.');
            return Promise.resolve();
        }
        return this.topicService.addProducersToTopic(this.selectedProducer.id, this.selectedEnvironment.id, this.topic.name).then(
            () => {
                this.toasts.addSuccessToast('Producer wurde erfolgreich hinzugefügt.');
                this.closeModal.emit();
            },
            err => this.toasts.addHttpErrorToast('Producer konnte nicht hinzugefügt werden.', err))
            .finally(() => this.selectedProducer = null);
    }

}
