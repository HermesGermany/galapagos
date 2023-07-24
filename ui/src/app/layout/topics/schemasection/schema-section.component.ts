import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { SchemaMetadata, Topic, TopicsService, TopicSubscription } from '../../../shared/services/topics.service';
import { map, shareReplay } from 'rxjs/operators';
import { EnvironmentsService, KafkaEnvironment } from '../../../shared/services/environments.service';
import { ToastService } from '../../../shared/modules/toast/toast.service';
import { firstValueFrom, Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ServerInfoService } from '../../../shared/services/serverinfo.service';
import { AuthService } from '../../../shared/services/auth.service';

@Component({
    selector: 'app-schema-section',
    templateUrl: './schema-section.component.html',
    styleUrls: ['./schema-section.component.scss']
})
export class SchemaSectionComponent implements OnInit, OnChanges {

    @Input() topic: Topic;

    @Input() topicSubscribers: TopicSubscription[];

    @Input() isOwnerOfTopic: boolean;

    selectedEnvironment: Observable<KafkaEnvironment>;

    topicSchemas: Promise<SchemaMetadata[]>;

    nextStageSchemasMap: Promise<Map<number, boolean>>;

    topicSchemasNextStage: SchemaMetadata[];

    selectedSchemaVersion: SchemaMetadata;

    loadingSchemas: boolean;

    editSchemaMode = false;

    newSchemaText = '';

    schemaChangeDescription: string;

    currentText: Observable<string>;

    schemaDeleteWithSub: Observable<boolean>;

    isAdmin: Observable<boolean>;

    skipCompatCheck = false;

    environments: Observable<KafkaEnvironment[]>;

    existSchemaOnNextVersion = true;

    constructor(
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private translateService: TranslateService,
        private toasts: ToastService,
        private modalService: NgbModal,
        private serverInfo: ServerInfoService,
        authService: AuthService
    ) {
        this.currentText = translateService.stream('(current)');
        this.isAdmin = authService.admin;
    }

    ngOnInit(): void {
        this.environmentsService.getCurrentEnvironment().subscribe({
            next: env => {
                if (this.topic && env) {
                    this.loadSchemas(this.topic, env.id);
                }
            }
        });
        this.schemaDeleteWithSub = this.serverInfo.getServerInfo()
            .pipe(map(info => info.toggles.schemaDeleteWithSub === 'true')).pipe(shareReplay(1));

        this.selectedEnvironment = this.environmentsService.getCurrentEnvironment();
    }

    async ngOnChanges(changes: SimpleChanges) {
        if (changes.topic) {
            const change = changes.topic;
            if (change.currentValue) {
                const env = await firstValueFrom(this.environmentsService.getCurrentEnvironment());
                this.loadSchemas(change.currentValue, env.id);
            }
        }
    }

    schemaUrl(schemaVersion: SchemaMetadata) {
        return window.location.origin + '/schema/' + schemaVersion.id;
    }

    startEditSchemaMode() {
        this.editSchemaMode = true;
        this.newSchemaText = '';
    }

    async checkIfSchemaVersionExistsOnNextVersion(): Promise<void> {
        this.existSchemaOnNextVersion = true;
        const nextStageSchemas = await this.nextStageSchemasMap;
        const existOnNextStage = nextStageSchemas[this.selectedSchemaVersion.schemaVersion];
        this.existSchemaOnNextVersion = !!existOnNextStage;
    }

    async publishNewSchema(): Promise<any> {
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment());
        return this.topicService.addTopicSchema(this.topic.name, environment.id, this.newSchemaText,
            this.skipCompatCheck, this.schemaChangeDescription).then(
            () => {
                this.editSchemaMode = false;
                this.toasts.addSuccessToast('SCHEMA_PUBLISH_SUCCESS');
                this.loadSchemas(this.topic, environment.id);
            },
            err => this.toasts.addHttpErrorToast('SCHEMA_PUBLISH_ERROR', err)
        );
    }

    async deleteLatestSchema(): Promise<any> {
        const environment = await firstValueFrom(this.environmentsService.getCurrentEnvironment());

        return this.topicService.deleteLatestSchema(this.topic.name, environment.id).then(
            () => {
                this.toasts.addSuccessToast('SCHEMA_DELETE_SUCCESS');
                this.loadSchemas(this.topic, environment.id);
            },
            err => this.toasts.addHttpErrorToast('SCHEMA_DELETE_ERROR', err)
        );
    }

    openDeleteConfirmDlg(content: any) {
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    /**
     async checkIfDeleteIsAllowed() {
        const env = await this.environmentsService.getCurrentEnvironment();
        const n = this.schemaDeleteWithSub = this.serverInfo.getServerInfo()
            .pipe(map(info => info.toggles.schemaDeleteWithSub === 'true')).pipe(shareReplay(1));
        return this.selectedSchemaVersion.isLatest && this.isOwnerOfTopic && !this.existSchemaOnNextVersion && (this.topicSubscribers?.length === 0
            || (!env.stagingOnly && (this.schemaDeleteWithSub | async)));
    }**/

    loadSchemas(topic: Topic, environmentId: string) {
        this.loadingSchemas = true;
        this.topicSchemas = this.topicService
            .getTopicSchemas(topic.name, environmentId)
            .then(schemas => schemas.reverse())
            .then(schemas => {
                this.environmentsService.getEnvironments();
                this.selectedSchemaVersion = schemas.length ? schemas[0] : null;
                if (this.selectedSchemaVersion) {
                    this.nextStageSchemasMap = this.environmentsService.checkIfSchemaCanGetDeleted(environmentId, this.topic);
                    this.checkIfSchemaVersionExistsOnNextVersion();
                }
                return schemas;
            })
            .finally(() => (this.loadingSchemas = false));
    }

    exitSchemaMode() {
        this.editSchemaMode = false;
        const element = document.body.getElementsByTagName('app-topic-metadata-table')[0];
        element.scrollIntoView({ block:'start' });
    }

}
