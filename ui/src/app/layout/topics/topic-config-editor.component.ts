import { Component, OnInit, Input, ChangeDetectorRef, AfterViewChecked } from '@angular/core';
import { TopicConfigValues, TopicsService, TopicConfigDescriptor, TopicUpdateConfigValue } from '../../shared/services/topics.service';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';
import { firstValueFrom, Observable } from 'rxjs';
import {  shareReplay, map, take, flatMap } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';
import { routerTransition } from '../../router.animations';
import { ToastService } from 'src/app/shared/modules/toast/toast.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'app-topic-config-editor',
    templateUrl: './topic-config-editor.component.html',
    styleUrls: ['./topic-config-editor.component.scss'],
    animations: [routerTransition()]
})
export class TopicConfigEditorComponent implements OnInit, AfterViewChecked {
    @Input() config: TopicConfigValues;

    topicName: Observable<string>;

    environments: Observable<KafkaEnvironment[]>;

    allConfigurationProperties: Observable<TopicConfigDescriptor[]>;

    defaultTopicConfigs: { [env: string]: TopicConfigValues } = { };

    configuration: { [env: string]: TopicConfigValues } = { };

    constructor(private environmentsService: EnvironmentsService, private topicsService: TopicsService,
        private toasts: ToastService, private translateService: TranslateService, private route: ActivatedRoute,
        private changeDetector: ChangeDetectorRef) {
        this.topicName = this.route.params.pipe(map(params => params['name'] as string)).pipe(shareReplay(1));
    }

    ngOnInit() {
        this.environments = this.environmentsService.getEnvironments().pipe(flatMap(envs =>
            this.topicName.pipe(flatMap(topicName => this.topicsService.getEnvironmentsForTopic(topicName)))
                .pipe(map(envIds => envIds.map(id => envs.find(e => e.id === id))))
        )).pipe(shareReplay(1));

        this.allConfigurationProperties = this.topicsService.getSupportedConfigProperties();

        // for each environment, retrieve the topic and default configuration, and update our config maps
        firstValueFrom(this.environments.pipe(take(1))).then(envs => envs.forEach(env => {
            firstValueFrom(this.topicsService.getDefaultTopicConfig(env.id).pipe(take(1))).then(
                config => this.defaultTopicConfigs[env.id] = { ...config });
            firstValueFrom(this.topicName.pipe(take(1))).then(topicName =>
                firstValueFrom(this.topicsService.getTopicConfig(topicName, env.id).pipe(take(1))).then(
                    config => this.configuration[env.id] = { ...config }));
        }));
    }

    ngAfterViewChecked() {
        this.changeDetector.detectChanges();
    }

    isDefaultConfig(envId: string, configName: string) {
        return this.configuration[envId][configName] === this.defaultTopicConfigs[envId][configName];
    }

    resetConfig(envId: string, configName: string) {
        this.configuration[envId][configName] = this.defaultTopicConfigs[envId][configName];
    }

    async saveConfig(): Promise<void> {
        const envs = await firstValueFrom(this.environments.pipe(take(1)));
        const props = await firstValueFrom(this.allConfigurationProperties.pipe(take(1)));
        const topicName = await firstValueFrom(this.topicName.pipe(take(1)));

        let result = Promise.resolve();

        envs.forEach(env => {
            const config: TopicUpdateConfigValue[] = [];
            props.forEach(prop => {
                if (!this.isDefaultConfig(env.id, prop.configName)) {
                    config.push({ name: prop.configName, value: this.configuration[env.id][prop.configName] });
                }
            });
            result = result.then(() => this.topicsService.updateTopicConfig(topicName, env.id, config));
        });

        const successMsg = await firstValueFrom(this.translateService.get('TOPIC_CONFIG_UPDATE_SUCCESS').pipe(take(1)));
        const errorMsg = await firstValueFrom(this.translateService.get('TOPIC_CONFIG_UPDATE_ERROR').pipe(take(1)));

        return result.then(() => this.toasts.addSuccessToast(successMsg), err => this.toasts.addHttpErrorToast(errorMsg, err));
    }

}
