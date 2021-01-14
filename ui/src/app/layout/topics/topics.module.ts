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

export const getHighlightLanguages = () => ({
        json: () => import('highlight.js/lib/languages/json')
});

@NgModule({
    imports: [CommonModule, TopicsRoutingModule, TranslateModule, FormsModule, NgbModule,
        SpinnerWhileModule, HighlightModule],
    declarations: [TopicsComponent, TableSortDirective, SingleTopicComponent, TopicConfigEditorComponent],
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
