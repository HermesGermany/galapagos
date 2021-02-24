import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { SchemaMetadata, Topic, TopicsService, TopicSubscription } from '../../../shared/services/topics.service';
import { map, shareReplay, take } from 'rxjs/operators';
import { EnvironmentsService, KafkaEnvironment } from '../../../shared/services/environments.service';
import { ToastService } from '../../../shared/modules/toast/toast.service';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ServerInfoService } from '../../../shared/services/serverinfo.service';

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

    selectedSchemaVersion: SchemaMetadata;

    loadingSchemas: boolean;

    editSchemaMode = false;

    newSchemaText = '';

    schemaChangeDescription: string;

    currentText: Observable<string>;

    schemaDeleteWithSub: Observable<boolean>;

    constructor(
        private topicService: TopicsService,
        private environmentsService: EnvironmentsService,
        private translateService: TranslateService,
        private toasts: ToastService,
        private modalService: NgbModal,
        private serverInfo: ServerInfoService
    ) {
        this.currentText = translateService.stream('(current)');
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
                const env = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();
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

    async publishNewSchema(): Promise<any> {
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.addTopicSchema(this.topic.name, environment.id, this.newSchemaText, this.schemaChangeDescription).then(
            () => {
                this.editSchemaMode = false;
                this.toasts.addSuccessToast('Das Schema wurde erfolgreich veröffentlicht.');
                this.loadSchemas(this.topic, environment.id);
            },
            err => this.toasts.addHttpErrorToast('Das Schema konnte nicht veröffentlicht werden', err)
        );
    }

    async deleteLatestSchema(): Promise<any> {
        const environment = await this.environmentsService.getCurrentEnvironment().pipe(take(1)).toPromise();

        return this.topicService.deleteLatestSchema(this.topic.name, environment.id).then(
            () => {
                this.toasts.addSuccessToast('Das Schema wurde erfolgreich gelöscht.');
                this.loadSchemas(this.topic, environment.id);
            },
            err => this.toasts.addHttpErrorToast('Das Schema konnte nicht gelöscht werden', err)
        );
    }

    openDeleteConfirmDlg(content: any) {
        this.modalService.open(content, { ariaLabelledBy: 'modal-title', size: 'lg' });
    }

    loadSchemas(topic: Topic, environmentId: string) {
        this.loadingSchemas = true;
        this.topicSchemas = this.topicService
            .getTopicSchemas(topic.name, environmentId)
            .then(schemas => schemas.reverse())
            .then(schemas => {
                this.selectedSchemaVersion = schemas.length ? schemas[0] : null;
                return schemas;
            })
            .finally(() => (this.loadingSchemas = false));
    }

}
