import { Component, Input } from '@angular/core';
import { Topic, TopicsService } from '../../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../../shared/services/environments.service';
import { ToastService } from '../../../shared/modules/toast/toast.service';
import { take } from 'rxjs/operators';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

@Component({
    selector: 'app-delete-topic-component',
    templateUrl: './delete-topic.component.html'
})
export class DeleteTopicComponent {

    @Input() topic: Topic;

    @Input() isOwnerOfTopic: boolean;

    @Input() topicName: string;

    @Input() translateParams: any = {};

    @Input() selectedEnvironment: KafkaEnvironment;

    topicNameConfirmText = '';

    constructor(
        private toasts: ToastService,
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private modalService: NgbModal,
        private router: Router
    ) {
    }

    openDeleteConfirmDlg(content: any) {
        this.topicNameConfirmText = '';
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    openAddProducerDlg(content: any) {
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    async deleteTopic(): Promise<any> {
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment().pipe(take(1)));

        return this.topicService.deleteTopic(environment.id, this.topic.name).then(
            () => {
                this.toasts.addSuccessToast('TOPIC_DELETE_SUCCESS');
                this.router.navigateByUrl('/topics');
            },
            err => this.toasts.addHttpErrorToast('TOPIC_DELETE_ERROR', err)
        );
    }

    closeModal($event: any) {
        this.modalService.dismissAll();
    }
}
