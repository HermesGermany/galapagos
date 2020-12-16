import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ApplicationsRoutingModule } from './applications-routing.module';
import { ApplicationsComponent } from './applications.component';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { SpinnerWhileModule } from 'src/app/shared/modules/spinner-while/spinner-while.module';
import { OpensslCommandModule } from '../../shared/modules/openssl-command/openssl-command.module';

@NgModule({
    imports: [CommonModule, ApplicationsRoutingModule, TranslateModule, FormsModule, NgbModule, SpinnerWhileModule, OpensslCommandModule],
    declarations: [ApplicationsComponent]
})
export class ApplicationsModule {}
