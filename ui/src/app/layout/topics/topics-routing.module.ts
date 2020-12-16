import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { TopicsComponent } from './topics.component';
import { SingleTopicComponent } from './single-topic.component';
import { TopicConfigEditorComponent } from './topic-config-editor.component';

const routes: Routes = [
    {
        path: '', component: TopicsComponent
    },
    {
        path: ':name', component: SingleTopicComponent
    },
    {
        path: ':name/config', component: TopicConfigEditorComponent
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class TopicsRoutingModule {
}
