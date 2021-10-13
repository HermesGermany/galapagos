import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LayoutComponent } from './layout.component';

const routes: Routes = [
    {
        path: '',
        component: LayoutComponent,
        children: [
            { path: '', redirectTo: 'dashboard', pathMatch: 'prefix' },
            { path: 'dashboard', loadChildren: () => import('./dashboard/dashboard.module').then(m => m.DashboardModule) },
            { path: 'topics', loadChildren: () => import('./topics/topics.module').then(m => m.TopicsModule) },
            { path: 'createtopic', loadChildren: () => import('./createtopic/createtopic.module').then(m => m.CreateTopicModule) },
            { path: 'applications', loadChildren: () => import('./applications/applications.module').then(m => m.ApplicationsModule) },
            { path: 'staging', loadChildren: () => import('./staging/staging.module').then(m => m.StagingModule) },
            { path: 'admin', loadChildren: () => import('./admin/admin.module').then(m => m.AdminModule) },
            { path: 'user-settings', loadChildren: () => import('./user-settings/user-settings.module').then(m => m.UserSettingsModule) }
        ]
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class LayoutRoutingModule {}
