import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { ActivatedRoute } from '@angular/router';
import { Topic, TopicsService, TopicSubscription } from '../../shared/services/topics.service';
import { combineLatest, firstValueFrom, Observable } from 'rxjs';
import { finalize, map, shareReplay } from 'rxjs/operators';
import { ApplicationsService, UserApplicationInfo } from '../../shared/services/applications.service';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';

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

    availableApplications: Observable<UserApplicationInfo[]>;

    loadingApplications: Observable<boolean>;

    loadingSubscribers: boolean;

    selectedEnvironment: Observable<KafkaEnvironment>;

    translateParams: any = {};

    isOwnerOfTopic: Observable<boolean>;

    topicNameConfirmText = '';

    constructor(
        private route: ActivatedRoute,
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private applicationsService: ApplicationsService
    ) {
        route.queryParamMap.subscribe({
            next: params => {
                if (params.has('environment')) {
                    const envId = params.get('environment');
                    firstValueFrom(environmentsService
                        .getEnvironments()

                    ).then(envs => {
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
    }


    async refreshChildData() {
        const topic = await firstValueFrom(this.topic);
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment());
        this.loadSubscribers(topic, environment.id);
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

        firstValueFrom(this.topicSubscribers
        ).then(subs => {
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

}
