import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { TopicsRoutingModule } from './topics-routing.module';
import { TopicsComponent } from './topics.component';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TableSortDirective } from './sort.directive';
import { SingleTopicComponent } from './single-topic.component';
import { SpinnerWhileModule } from '../../shared/modules/spinner-while/spinner-while.module';
import { HIGHLIGHT_OPTIONS, HighlightModule } from 'ngx-highlightjs';
import { TopicConfigEditorComponent } from './topic-config-editor.component';
import { SchemaSectionComponent } from './schemasection/schema-section.component';
import { TopicMetadataTableComponent } from './topicmetadatatable/topic-metadata-table.component';
import { SubscriptionSectionComponent } from './subscribesection/subscribe-section.component';
import { DeprecationComponent } from './deprecation/deprecation.component';
import { DeleteTopicComponent } from './deletetopic/delete-topic.component';

export const getHighlightLanguages = () => ({
    json: () => import('highlight.js/lib/languages/json')
});

@NgModule({
    imports: [CommonModule, TopicsRoutingModule, TranslateModule, FormsModule, NgbModule,
        SpinnerWhileModule, HighlightModule],
    declarations: [TopicsComponent, TableSortDirective, SingleTopicComponent,
        TopicConfigEditorComponent, SchemaSectionComponent, TopicMetadataTableComponent,
        SubscriptionSectionComponent,
        DeprecationComponent, DeleteTopicComponent],
    providers: [
        {
            provide: HIGHLIGHT_OPTIONS,
            useValue: {
                languages: getHighlightLanguages(),
                lineNumbers: true
            }
        }
    ]
})
export class TopicsModule {
}
