import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LogoutSuccessComponent } from './logout-success.component';
import { LogoutSuccessRoutingModule } from './logout-success-routing.module';

@NgModule({
    imports: [
        CommonModule,
        LogoutSuccessRoutingModule
    ],
    declarations: [LogoutSuccessComponent]
})
export class LogoutSuccessModule {
}
