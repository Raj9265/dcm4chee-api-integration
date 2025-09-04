import {Component, Input, OnInit, ViewEncapsulation} from '@angular/core';
import {FormElement} from "../form/form-element";
import {FormGeneratorService} from "./form-generator.service";
import {FormsModule, NgForm} from '@angular/forms';
import * as _ from 'lodash-es';
import {CommonModule, NgClass, NgStyle, NgSwitch} from '@angular/common';
import {ComposedInputComponent} from '../../widgets/composed-input/composed-input.component';
import {IssuerSelectorComponent} from '../../widgets/issuer-selector/issuer-selector.component';
import {RangePickerComponent} from '../../widgets/range-picker/range-picker.component';
import {DcmDropDownComponent} from '../../widgets/dcm-drop-down/dcm-drop-down.component';

@Component({
  selector: 'form-generator',
  templateUrl: './form-generator.component.html',
  styleUrls: ['./form-generator.component.scss'],
  imports: [
    NgClass,
    NgSwitch,
    FormsModule,
    NgStyle,
    ComposedInputComponent,
    IssuerSelectorComponent,
    RangePickerComponent,
    DcmDropDownComponent,
    CommonModule
  ],
  standalone: true
})
export class FormGeneratorComponent implements OnInit{
  @Input() schema:FormElement<any>[];

  @Input() showLabels:boolean = false;
  _ = _;
  @Input() model:any = {};
  constructor(
      private service:FormGeneratorService
  ){}
  ngOnInit(): void {
    //this.model = this.service.convertSchemaToFormGroup(this.schema);
  }

/*  onSubmit(form: NgForm) {
    console.log("form",form);
  }*/

  modelChange(element: FormElement<any>, $event: any) {
    console.log("element",element);
    console.log("e",$event)
    console.log("model",this.model);
    if(element && element.onChangeHook){
      element?.onChangeHook(element,$event, this.model)
    }
  }
  focusOut(element: FormElement<any>, $event: any){
    if(element && element.onFocusOutHook){
      element?.onFocusOutHook(element,$event, this.model)
    }
  }
  dateChanged(element: FormElement<any>, $event: any){
    if($event){
      _.set(this.model,element.modelPath, $event);
    }
    //this.filterChange(e);
  }
}
