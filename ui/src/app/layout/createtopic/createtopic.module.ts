import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CreateTopicRoutingModule } from './createtopic-routing.module';
import { CreateTopicComponent } from './createtopic.component';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbAlertModule } from '@ng-bootstrap/ng-bootstrap';
import { SpinnerWhileModule } from 'src/app/shared/modules/spinner-while/spinner-while.module';

@NgModule({
    imports: [CommonModule, CreateTopicRoutingModule, TranslateModule, FormsModule, NgbAlertModule, SpinnerWhileModule],
    declarations: [CreateTopicComponent]
})
export class CreateTopicModule {}
