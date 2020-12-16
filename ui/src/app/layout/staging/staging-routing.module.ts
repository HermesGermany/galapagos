import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { StagingComponent } from './staging.component';

const routes: Routes = [
    {
        path: '', component: StagingComponent
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class StagingRoutingModule {
}
