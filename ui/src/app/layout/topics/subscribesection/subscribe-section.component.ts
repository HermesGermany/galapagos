import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ToastService } from '../../../shared/modules/toast/toast.service';
import { Topic, TopicsService } from '../../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../../shared/services/environments.service';
import { take } from 'rxjs/operators';
import { UserApplicationInfo } from '../../../shared/services/applications.service';
import { firstValueFrom, Observable } from 'rxjs';
import { ApiKeyService } from '../../../shared/services/apikey.service';

@Component({
    selector: 'app-subscription-section',
    templateUrl: './subscribe-section.component.html'
})
export class SubscriptionSectionComponent implements OnInit {

    @Input() topic: Topic;

    @Input() isOwnerOfTopic: boolean;

    @Input() availableApplications: UserApplicationInfo[];

    @Input() loadingApplications: boolean;

    @Output() appChanged = new EventEmitter();

    selectedEnvironment: Observable<KafkaEnvironment>;

    selectedApplication: UserApplicationInfo;

    showRegistrationWarning = false;

    subscriptionDescription: string;

    constructor(
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private toasts: ToastService,
        private apiKeyService: ApiKeyService
    ) {

    }

    ngOnInit(): void {
        this.selectedEnvironment = this.environmentsService.getCurrentEnvironment();
    }

    async checkApplicationApiKey() {
        if (!this.selectedApplication || !this.selectedEnvironment) {
            return;
        }
        try {
            const env = await firstValueFrom(this.selectedEnvironment.pipe(take(1)));
            const apikey = await this.apiKeyService.getApplicationApiKeysPromise(this.selectedApplication.id);
            this.showRegistrationWarning = !apikey.authentications[env.id];
        } catch (e) {
            this.toasts.addHttpErrorToast('APPLICATION_KEY_CHECK_ERROR', e);
        }
    }

    async subscribeToTopic(): Promise<any> {
        if (!this.selectedApplication) {
            return Promise.resolve();
        }

        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment().pipe(take(1)));

        return this.topicService.subscribeToTopic(this.topic.name, environment.id,
            this.selectedApplication.id, this.subscriptionDescription)
            .then(
                () => {
                    if (this.topic.subscriptionApprovalRequired) {
                        this.toasts.addSuccessToast('TOPIC_SUBSCRIBE_INFORMATION');
                    } else {
                        this.toasts.addSuccessToast('TOPIC_SUBSCRIPTION_SUCCESS');
                    }
                    this.appChanged.emit();
                },
                err => this.toasts.addHttpErrorToast('TOPIC_SUBSCRIPTION_ERROR', err)
            )
            .finally(() => this.subscriptionDescription = null);
    }

}
