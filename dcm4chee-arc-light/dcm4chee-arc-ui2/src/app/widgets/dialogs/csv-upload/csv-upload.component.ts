import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {ReactiveFormsModule, UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators} from '@angular/forms';
//import { MatLegacyDialogRef as MatDialogRef } from "@angular/material/legacy-dialog";
import {CsvUploadService} from "./csv-upload.service";
import {AppService} from "../../../app.service";
import {j4care} from "../../../helpers/j4care.service";
import * as _ from "lodash-es";
import {MatDialogRef} from "@angular/material/dialog";
import {CommonModule, NgStyle, NgSwitch} from '@angular/common';
import {MatOption, MatSelect} from '@angular/material/select';
import {RangePickerComponent} from '../../range-picker/range-picker.component';

@Component({
    selector: 'csv-upload',
    templateUrl: './csv-upload.component.html',
    styleUrls: ['./csv-upload.component.scss'],
    imports: [
        ReactiveFormsModule,
        NgSwitch,
        MatSelect,
        MatOption,
        RangePickerComponent,
        NgStyle,
        CommonModule
    ],
    standalone: true
})
export class CsvUploadComponent implements OnInit {

    form: UntypedFormGroup;
    csvFile:File;
    aes;
    params = {
        formSchema:[],
        prepareUrl:undefined
    };
    showLoader = false;
    model = {};
    constructor(
        public dialogRef: MatDialogRef<CsvUploadComponent>,
        private _fb: UntypedFormBuilder,
        private service:CsvUploadService,
        private appService:AppService
    ){}
    inputChanged(form, e){
        console.log("form",form)
        console.log("this.fomr",this.form)
        console.log("e",e)
        console.log("e",e.target.checked);
        if(form.type === "checkbox"){
            this.form.controls[form.filterKey].setValue(e.target.checked);
        }
    }
    ngOnInit() {
        console.log("formSchema",this.params);
        let formContent = {};
        this.params.formSchema.forEach(form=>{
            if(form.type === "checkbox"){
                formContent[form.filterKey] =  [null];
            }else{
                formContent[form.filterKey] =[j4care.getValue(form.filterKey, this.params, form.defaultValue), form.validation]
            }
        });
        this.form = this._fb.group(formContent);
    }
    submit(){
        this.showLoader = true;
        let semicolon:boolean = false;
        let url = this.params.prepareUrl(this.form.value)
        if(_.hasIn(this.form.value,"semicolon") && this.form.value["semicolon"]){
            semicolon = true;
        }
        this.service.uploadCSV(url, this.csvFile, semicolon, (end)=>{
            this.showLoader = false;
            if(end.status >= 199 && end.status < 300){
                let msgObject = {
                    msg:"",
                    status:"info"
                }
                try{
                    if(end.response){
                        let countObject = JSON.parse(end.response);
                        msgObject.msg = $localize `:@@tasks_created:${countObject.count}:count: tasks created successfully!`
                    }else{
                        const warning = end.getResponseHeader("warning");
                        msgObject.status = "warning";
                        if(warning){
                            msgObject.msg = warning;
                        }else{
                            msgObject.msg = $localize `:@@csv-upload.count_could_not_be_extracted:Count could not be extracted`;
                        }
                    }
                }catch (e){
                    console.log($localize `:@@csv-upload.count_could_not_be_extracted:Count could not be extracted`,e)
                }
                if(!msgObject.msg){
                    msgObject.msg = $localize `:@@task_created:Tasks created successfully!`
                }
                this.appService.setMessage({
                    "text":msgObject.msg,
                    "status":msgObject.status
                });
                this.dialogRef.close(this.form.value);
            }else{
                let msgObject = {
                    msg:"",
                    status:"error"
                }
                const warning = end.getResponseHeader("warning");
                if(warning){
                        msgObject.msg = warning;
                    }else{
                        if(end.response){
                            try{
                                let countObject = JSON.parse(end.response);
                                msgObject.msg = countObject.errorMessage;
                            }catch (e){
                                console.log($localize `:@@csv-upload.count_could_not_be_extracted:Count could not be extracted`,e)
                            }
                        }
                    }

                if(!msgObject.msg){
                    msgObject.msg = $localize `:@@upload_failed_please_try_again_later:Upload failed, please try again later!`
                }
                this.appService.setMessage({
                    "text":msgObject.msg,
                    "status":"error"
                });
                this.dialogRef.close(null);
            }
        },(err)=>{
            this.showLoader = false;
            this.appService.showError($localize `:@@upload_failed_please_try_again_later:Upload failed, please try again later!`)
            this.dialogRef.close(null);
        });
    }
    dateChanged(key, e){
        (<UntypedFormControl>this.form.controls[key]).setValue(e);
        if(e){
            this.model[key] = e;
        }else{
            delete this.model[key];
        }
    }
    onFileChange(e){
        console.log("e",e.target.files[0]);
        this.csvFile = e.target.files[0];
    }
}
