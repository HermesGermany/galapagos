import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LogoutSuccessComponent } from './logout-success.component';

const routes: Routes = [
    {
        path: '', component: LogoutSuccessComponent
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class LogoutSuccessRoutingModule {
}
