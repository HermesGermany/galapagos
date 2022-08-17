import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import {
    Change,
    ChangelogEntry,
    EnvironmentServerInfo,
    EnvironmentsService,
    KafkaEnvironment
} from '../../shared/services/environments.service';
import { firstValueFrom, Observable, switchMap } from 'rxjs';
import { flatMap, map, mergeMap, shareReplay, startWith, tap } from 'rxjs/operators';
import { CustomLink, ServerInfo, ServerInfoService } from '../../shared/services/serverinfo.service';
import { DateTime } from 'luxon';
import { TranslateService } from '@ngx-translate/core';
import { ApplicationInfo, ApplicationsService } from '../../shared/services/applications.service';
import { Location } from '@angular/common';
import { toNiceTimestamp } from '../../shared/util/time-util';
import { Md5 } from 'ts-md5';

@Component({
    selector: 'app-dashboard',
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss'],
    animations: [routerTransition()]
})
export class DashboardComponent implements OnInit {

    allEnvironments: Observable<KafkaEnvironment[]>;

    selectedEnvironment: Observable<KafkaEnvironment>;

    serverInfos: Observable<EnvironmentServerInfo[]>;

    appServerInfo: Observable<ServerInfo>;

    customLinks: Observable<CustomLink[]>;

    kafkaVersion: Observable<string>;

    changelog: Observable<ChangelogEntry[]>;

    frameworkConfigTemplate: Observable<string>;

    configTemplatesCollapsed = true;

    configTemplatesCopiedValue = false;

    changelogProfilePicture: string;

    changelogDefaultPicture: string;

    customImageUrl: string;

    constructor(private environments: EnvironmentsService, private applicationsService: ApplicationsService,
                private serverInfoService: ServerInfoService, private location: Location,
                private translate: TranslateService) {
        this.allEnvironments = environments.getEnvironments();
        this.selectedEnvironment = environments.getCurrentEnvironment();
        this.serverInfos = environments.getCurrentEnvironmentServerInfo();

        const uiConfigObs = this.serverInfoService.getUiConfig().pipe(shareReplay(1));
        this.changelog = uiConfigObs.pipe(switchMap(config =>
            this.selectedEnvironment
                .pipe(flatMap(env => this.environments.getChangeLog(env.id)))
                .pipe(map(changes => this.formatChanges(changes, config.changelogEntries, config.changelogMinDays)))
                .pipe(shareReplay(1))));
        firstValueFrom(uiConfigObs).then(config => {
            this.changelogProfilePicture = config.profilePicture;
            this.changelogDefaultPicture = config.defaultPicture;
            this.customImageUrl = config.customImageUrl;
        });

        this.customLinks = uiConfigObs.pipe(map(config => config.customLinks));
    }

    ngOnInit() {
        this.appServerInfo = this.serverInfoService.getServerInfo();
        this.updateConfigTemplate('spring');
        this.kafkaVersion = this.selectedEnvironment.pipe(flatMap(env => this.serverInfoService.getKafkaVersion(env.id)));
    }

    selectEnvironment(envId: string) {
        firstValueFrom(this.allEnvironments).then(
            envs => this.environments.setCurrentEnvironment(envs.find(env => env.id === envId)));
    }

    updateConfigTemplate(framework: string) {
        this.frameworkConfigTemplate = this.selectedEnvironment
            .pipe(tap(() => this.configTemplatesCopiedValue = false))
            .pipe(flatMap(env => this.environments.getFrameworkConfigTemplate(env.id, framework)));
    }

    agoString(timestamp: string): string {
        return DateTime.fromISO(timestamp).toLocal(this.translate.currentLang).toRelative();
    }

    agoTimeStamp(timestamp: string): Observable<string> {
        return toNiceTimestamp(timestamp, this.translate);
    }

    getInitialsProfilePicture(user: string): string {
        const name = user.split('@')[0].replace('.', '+');
        return `https://ui-avatars.com/api/?name=${name}&background=random`;
    }

    getCustomProfilePicture(user: string): string {
        return this.customImageUrl.replace('{0}', user);
    }

    getGravatarProfilePicture(user: string): string {
        const md5 = new Md5();
        md5.appendStr(user.trim().toLowerCase());
        const hash = md5.end();
        return `https://www.gravatar.com/avatar/${hash}?d=identicon`;
    }

    getProfilePicture(user: string, pictureType: string): string {
        if (pictureType === 'profile') {
            switch (this.changelogProfilePicture.toLowerCase()) {
                case 'custom':
                    return this.getCustomProfilePicture(user);
                case 'gravatar':
                    return this.getGravatarProfilePicture(user);
                case 'initials':
                    return this.getInitialsProfilePicture(user);
                default:
                    return '/assets/images/default_avatar.png';
            }
        } else if (pictureType === 'default') {
            switch (this.changelogDefaultPicture.toLowerCase()) {
                case 'gravatar':
                    return this.getGravatarProfilePicture(user);
                case 'initials':
                    return this.getInitialsProfilePicture(user);
                default:
                    return '/assets/images/default_avatar.png';
            }
        }
    }

    copyValueFromObservable(observer: Observable<string>) {
        const selBox = document.createElement('textarea');
        selBox.style.position = 'fixed';
        selBox.style.left = '0';
        selBox.style.top = '0';
        selBox.style.opacity = '0';
        const subscription = observer.subscribe(value => {
            selBox.value = value;
            document.body.appendChild(selBox);
            selBox.focus();
            selBox.select();
            navigator.clipboard.writeText(value);
            document.body.removeChild(selBox);
            this.configTemplatesCopiedValue = true;
            subscription.unsubscribe();
        });
    }

    private formatChanges(changes: ChangelogEntry[], amountOfEntries: number, minDays: number): ChangelogEntry[] {
        changes = changes
            .map(change => {
                change.change.html = this.changeHtml(change.change);
                change.profilePictureUrl = this.getProfilePicture(change.principal, 'profile');
                change.defaultPictureUrl = this.getProfilePicture(change.principal, 'default');
                return change;
            }).filter(change => change.change.html !== null);

        // reduce matching JSON Schema Version add / delete until no matching pairs can be found
        let reducedChanges = [...changes];
        let hasReduced = false;
        do {
            const oldLength = reducedChanges.length;
            reducedChanges = this.reduceChangelogByJsonSchemaDeletions(reducedChanges);
            hasReduced = reducedChanges.length !== oldLength;
        }
        while (hasReduced);

        const index = changes.findIndex(
            change =>
                new Date(change.timestamp) <
                new Date(new Date().setDate(new Date().getDate() - minDays)));

        return reducedChanges.slice(0, Math.max(amountOfEntries, index));
    }

    private reduceChangelogByJsonSchemaDeletions(changes: ChangelogEntry[]): ChangelogEntry[] {
        const reducingList: { entry: ChangelogEntry; toDelete: boolean }[] =
            changes.map(change => ({ entry: change, toDelete: false }));

        reducingList.forEach((value, index) => {
            if (value.entry.change.changeType === 'TOPIC_SCHEMA_VERSION_DELETED') {
                const matchingIndex = this.findMatchingSchemaAdd(changes, value.entry, index);
                if (matchingIndex > -1) {
                    value.toDelete = true;
                    reducingList[matchingIndex].toDelete = true;
                }
            }
        });

        return changes.filter((change, index) => !reducingList[index].toDelete);
    }

    // noinspection JSMethodCanBeStatic
    private findMatchingSchemaAdd(changes: ChangelogEntry[], change: ChangelogEntry, fromIndex: number): number {
        for (let i = fromIndex + 1; i < changes.length; i++) {
            const otherChange = changes[i].change;
            if (otherChange.topicName === change.change.topicName) {
                if (otherChange.changeType === 'TOPIC_SCHEMA_VERSION_PUBLISHED') {
                    return i;
                }
                // anything else regarding this topic "in the way"
                return -1;
            }
        }
        return -1;
    }

    private changeHtml(change: Change): Observable<string> {
        let topicName: string;
        let topicLink: string;
        let state: string;

        switch (change.changeType) {
            case 'TOPIC_CREATED':
                if (change.topicMetadata.type === 'INTERNAL') {
                    return null;
                }
                topicName = change.topicMetadata.name;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                return this.translate.stream('CHANGELOG_TOPIC_CREATED_HTML', {
                    topicName: topicName,
                    topicLink: topicLink
                });
            case 'TOPIC_DELETED':
                if (change.internalTopic) {
                    return null;
                }
                topicName = change.topicName;
                return this.translate.stream('CHANGELOG_TOPIC_DELETED_HTML', { topicName: topicName });
            case 'TOPIC_DEPRECATED':
                topicName = change.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                const obsLang = this.translate.onLangChange.pipe(map(evt => evt.lang))
                    .pipe(startWith(this.translate.currentLang)).pipe(shareReplay(1));
                const obsEolDate = obsLang.pipe(map(lang => DateTime.fromISO(change.eolDate).setLocale(lang).toLocaleString({
                    month: '2-digit',
                    day: '2-digit',
                    year: 'numeric'
                })));
                return obsEolDate.pipe(map(date => this.translate.instant('CHANGELOG_TOPIC_DEPRECATED_HTML', {
                    topicName: topicName,
                    topicLink: topicLink,
                    date: date
                })));
            case 'TOPIC_UNDEPRECATED':
                topicName = change.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                return this.translate.stream('CHANGELOG_TOPIC_UNDEPRECATED_HTML', {
                    topicName: topicName,
                    topicLink: topicLink
                });
            case 'TOPIC_SCHEMA_VERSION_PUBLISHED':
                topicName = change.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                return this.translate.stream('CHANGELOG_TOPIC_SCHEMA_VERSION_REGISTERED_HTML',
                    { topicName: topicName, topicLink: topicLink });
            case 'TOPIC_SCHEMA_VERSION_DELETED':
                topicName = change.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                return this.translate.stream('CHANGELOG_TOPIC_SCHEMA_VERSION_DELETED_HTML',
                    { topicName: topicName, topicLink: topicLink });
            case 'TOPIC_DESCRIPTION_CHANGED':
                if (change.internalTopic) {
                    return null;
                }
                topicName = change.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                return this.translate.stream('CHANGELOG_TOPIC_DESCRIPTION_CHANGE_HTML', {
                    topicName: topicName,
                    topicLink: topicLink
                });
            case 'TOPIC_SUBSCRIBED':
                state = change.subscriptionMetadata.state;
                topicName = change.subscriptionMetadata.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                return this.applicationInfo(change.subscriptionMetadata.clientApplicationId).pipe(
                    flatMap(app => {
                        if (!app) {
                            return this.translate.stream('(unknown)').pipe(
                                flatMap(s => this.translate.stream(
                                    state === 'APPROVED'
                                        ? 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_NO_APP_LINK'
                                        : 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_NO_APP_LINK_PENDING',
                                    { topicName: topicName, topicLink: topicLink, appInfo: { name: s } })));
                        }
                        if (!app.infoUrl) {
                            return this.translate.stream(
                                state === 'APPROVED'
                                    ? 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_NO_APP_LINK'
                                    : 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_NO_APP_LINK_PENDING',
                                { topicName: topicName, topicLink: topicLink, appInfo: app });
                        }

                        return this.translate.stream(
                            state === 'APPROVED'
                                ? 'CHANGELOG_TOPIC_SUBSCRIBED_HTML'
                                : 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_PENDING',
                            { topicName: topicName, topicLink: topicLink, appInfo: app });
                    }));
            case 'TOPIC_UNSUBSCRIBED':
                topicName = change.subscriptionMetadata.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                return this.applicationInfo(change.subscriptionMetadata.clientApplicationId).pipe(
                    flatMap(app => {
                        if (!app) {
                            return this.translate.stream('(unknown)').pipe(
                                flatMap(s => this.translate.stream('CHANGELOG_TOPIC_UNSUBSCRIBED_HTML_NO_APP_LINK',
                                    { topicName: topicName, topicLink: topicLink, appInfo: { name: s } })));
                        }
                        if (!app.infoUrl) {
                            return this.translate.stream('CHANGELOG_TOPIC_UNSUBSCRIBED_HTML_NO_APP_LINK',
                                { topicName: topicName, topicLink: topicLink, appInfo: app });
                        }
                        return this.translate.stream('CHANGELOG_TOPIC_UNSUBSCRIBED_HTML',
                            { topicName: topicName, topicLink: topicLink, appInfo: app });
                    }));
            case 'TOPIC_PRODUCER_APPLICATION_ADDED':
                topicName = change.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                const producerApplicationId = change.producerApplicationId;
                return this.applicationInfo(producerApplicationId).pipe(mergeMap(
                    producer => this.translate.stream('CHANGELOG_PRODUCER_ADDED_HTML', {
                        topicName: topicName,
                        topicLink: topicLink,
                        producerName: producer.name
                    })
                ));
            case 'TOPIC_SUBSCRIPTION_UPDATED':
                state = change.subscriptionMetadata.state;
                topicName = change.subscriptionMetadata.topicName;
                topicLink = this.urlForRouterLink('/topics/' + topicName);
                return this.applicationInfo(change.subscriptionMetadata.clientApplicationId).pipe(
                    flatMap(app => {
                        if (!app) {
                            return this.translate.stream('(unknown)').pipe(
                                flatMap(s => this.translate.stream(
                                    state === 'APPROVED'
                                        ? 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_NO_APP_LINK_APPROVED'
                                        : 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_NO_APP_LINK_DECLINED',
                                    { topicName: topicName, topicLink: topicLink, appInfo: { name: s } })));
                        }
                        if (!app.infoUrl) {
                            return this.translate.stream(
                                state === 'APPROVED'
                                    ? 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_NO_APP_LINK_APPROVED'
                                    : 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_NO_APP_LINK_DECLINED',
                                { topicName: topicName, topicLink: topicLink, appInfo: app });
                        }

                        return this.translate.stream(
                            state === 'APPROVED'
                                ? 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_APPROVED'
                                : 'CHANGELOG_TOPIC_SUBSCRIBED_HTML_DECLINED',
                            { topicName: topicName, topicLink: topicLink, appInfo: app });
                    }));

        }
        return null;
    }

    private urlForRouterLink(routerLink: string): string {
        return this.location.prepareExternalUrl(routerLink);
    }

    private applicationInfo(applicationId: string): Observable<ApplicationInfo> {
        return this.applicationsService.getAvailableApplications(false).pipe(map(apps => apps.find(app => app.id === applicationId)));
    }
}
