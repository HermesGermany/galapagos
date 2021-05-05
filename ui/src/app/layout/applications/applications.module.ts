import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApplicationsRoutingModule } from './applications-routing.module';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { SpinnerWhileModule } from 'src/app/shared/modules/spinner-while/spinner-while.module';
import { ApplicationBlockComponent } from './application-block.component';
import { ApplicationsComponent } from './applications.component';

@NgModule({
    imports: [CommonModule, ApplicationsRoutingModule, TranslateModule, FormsModule, NgbModule, SpinnerWhileModule],
    declarations: [ApplicationsComponent, ApplicationBlockComponent]
})
export class ApplicationsModule {
}
