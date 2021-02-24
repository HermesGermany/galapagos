import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ToastService } from '../../../shared/modules/toast/toast.service';
import { Topic, TopicsService } from '../../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../../shared/services/environments.service';
import { take } from 'rxjs/operators';
import { UserApplicationInfo } from '../../../shared/services/applications.service';
import { CertificateService } from '../../../shared/services/certificates.service';
import { Observable } from 'rxjs';

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
        private translateService: TranslateService,
        private toasts: ToastService,
        private certificateService: CertificateService
    ) {

    }

    ngOnInit(): void {
        this.selectedEnvironment = this.environmentsService.getCurrentEnvironment();
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

    async subscribeToTopic(): Promise<any> {
        if (!this.selectedApplication) {
            return Promise.resolve();
        }

        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.subscribeToTopic(this.topic.name, environment.id,
            this.selectedApplication.id, this.subscriptionDescription)
            .then(
                () => {
                    if (this.topic.subscriptionApprovalRequired) {
                        this.toasts.addSuccessToast('Die Topic-Owner wurden über die Abonnement-Anfrage informiert');
                    } else {
                        this.toasts.addSuccessToast('Die Anwendung hat das Topic nun abonniert');
                    }
                    this.appChanged.emit();
                },
                err => this.toasts.addHttpErrorToast('Das Abonnement konnte nicht erstellt werden', err)
            )
            .finally(() => this.subscriptionDescription = null);
    }

}
