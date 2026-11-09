import { Component, ElementRef, ViewChild, computed, inject, signal, OnDestroy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from './api.service';
import { Place, Stage } from './models';
import { MediaGalleryComponent } from './media-gallery.component';

type Panel = 'detail' | 'report' | 'edit' | 'cleanup' | 'after' | 'live' | 'help';
interface Preview { file: File; url: string; }

@Component({ selector: 'app-root', standalone: true, imports: [ReactiveFormsModule, MediaGalleryComponent], templateUrl: './app.component.html' })
export class AppComponent implements OnDestroy {
  readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  @ViewChild('dialog', { static: true }) dialog!: ElementRef<HTMLDialogElement>;
  readonly places = signal<Place[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly mode = signal<'all' | 'mine'>('all');
  readonly query = signal('');
  readonly countryFilter = signal('');
  readonly stage = signal<Stage | 'all'>('all');
  readonly panel = signal<Panel>('detail');
  readonly selectedId = signal<string | null>(null);
  readonly selected = computed(() => this.places().find(p => p.id === this.selectedId()));
  readonly busy = signal(false);
  readonly busyId = signal('');
  readonly formError = signal('');
  readonly previews = signal<Preview[]>([]);
  readonly draftRestored = signal(false);
  readonly statusOptions: { key: Stage | 'all'; label: string }[] = [
    { key: 'all', label: 'All places' }, { key: 'reported', label: 'Needs cleaning' },
    { key: 'organized', label: 'Cleanup planned' }, { key: 'cleaned', label: 'Results shared' }
  ];
  readonly userTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  readonly timeZones: string[] = ['UTC', ...((Intl as unknown as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf?.('timeZone') || [this.userTimeZone])];
  private readonly regionNames = new Intl.DisplayNames(['en'], { type: 'region' });
  readonly countries = 'AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW'.split(' ').map(code => ({ code, name: this.regionNames.of(code) || code })).sort((a,b) => a.name.localeCompare(b.name));
  readonly activityCount = computed(() => this.places().filter(p => p.mine || p.organizerMine || p.joined).length);
  readonly visible = computed(() => this.places().filter(p =>
    (this.mode() === 'all' || p.mine || p.organizerMine || p.joined) &&
    (this.stage() === 'all' || p.stage === this.stage()) &&
    (!this.countryFilter() || p.country === this.countryFilter()) &&
    `${p.title} ${p.location} ${p.city} ${this.countryName(p.country)}`.toLowerCase().includes(this.query().toLowerCase())
  ));
  readonly form = this.fb.nonNullable.group({
    title: [''], location: [''], city: [''], country: [''], description: [''],
    organizerName: [''], date: [''], time: ['09:00'], timeZone: [this.userTimeZone], target: [10], supplies: [''],
    result: [''], url: [''], consent: [false]
  });
  private toastTimer?: ReturnType<typeof setTimeout>;
  constructor() {
    void this.refresh();
    this.form.valueChanges.subscribe(() => {
      if (this.panel() === 'report') {
        const { title, location, city, country, description } = this.form.getRawValue();
        try { localStorage.setItem('cg.report-draft', JSON.stringify({ title, location, city, country, description })); } catch { /* drafts are optional */ }
      }
    });
  }
  async refresh(): Promise<boolean> {
    this.loading.set(true);this.error.set('');
    try { const response = await this.api.list();this.places.set(response.places);return true; }
    catch(e) { this.error.set(this.api.error(e));return false; }
    finally { this.loading.set(false); }
  }
  navigate(mode: 'all' | 'mine') { this.mode.set(mode);this.clearFilters();document.getElementById('feed')?.scrollIntoView({ behavior: 'smooth' }); }
  clearFilters() { this.query.set('');this.stage.set('all');this.countryFilter.set(''); }
  inputValue(event: Event) { return (event.target as HTMLInputElement).value; }
  countryName(code: string) { return this.regionNames.of(code) || code; }
  status(p: Place) { return this.statusOptions.find(s => s.key === p.stage)?.label || p.stage; }
  time(p: Place, original = false) {
    if (!p.cleanupAt) return '';
    return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: original ? p.timeZone || 'UTC' : this.userTimeZone }).format(new Date(p.cleanupAt));
  }
  heroMedia(p: Place) { return p.stage === 'cleaned' && p.after.length ? p.after[0] : p.before[0]; }
  show(panel: Panel, p?: Place) {
    if (this.busy()) return;
    this.panel.set(panel);this.selectedId.set(p?.id || null);this.formError.set('');this.clearFiles();this.draftRestored.set(false);
    const f=this.form;
    for (const c of Object.values(f.controls)) c.clearValidators();
    f.reset({ title:'',location:'',city:'',country:'',description:'',organizerName:'',date:'',time:'09:00',timeZone:this.userTimeZone,target:10,supplies:'',result:'',url:'',consent:false },{emitEvent:false});
    if (panel === 'report' || panel === 'edit') {
      for(const name of ['title','location','city','country','description'] as const) f.controls[name].setValidators([Validators.required]);
      f.controls.title.addValidators(Validators.maxLength(100));f.controls.location.addValidators(Validators.maxLength(180));
      f.controls.city.addValidators(Validators.maxLength(100));f.controls.description.addValidators(Validators.maxLength(1500));
      if(p)f.patchValue({title:p.title,location:p.location,city:p.city,country:p.country,description:p.description},{emitEvent:false});
      if(panel==='report')try { const draft=JSON.parse(localStorage.getItem('cg.report-draft') || 'null');if(draft){f.patchValue(draft,{emitEvent:false});this.draftRestored.set(true);} } catch { /* ignore a malformed draft */ }
    }
    if(panel==='cleanup'){
      for(const name of ['organizerName','date','time','timeZone','supplies'] as const)f.controls[name].setValidators([Validators.required]);
      f.controls.organizerName.addValidators(Validators.maxLength(100));f.controls.supplies.addValidators(Validators.maxLength(1500));
      f.controls.target.setValidators([Validators.required,Validators.min(1),Validators.max(500)]);
    }
    if(panel==='after')f.controls.result.setValidators([Validators.required,Validators.maxLength(1500)]);
    if(panel==='live'){
      f.controls.url.setValidators([Validators.required,Validators.maxLength(2048),Validators.pattern(/^https:\/\/.+/)]);
      f.controls.consent.setValidators([Validators.requiredTrue]);f.patchValue({url:p?.live?.url || ''},{emitEvent:false});
    }
    for(const c of Object.values(f.controls))c.updateValueAndValidity({emitEvent:false});
    f.markAsPristine();f.markAsUntouched();
    if(!this.dialog.nativeElement.open)this.dialog.nativeElement.showModal();this.dialog.nativeElement.scrollTop=0;
  }
  close(event?: Event) { if(this.busy()){event?.preventDefault();return;}this.dialog.nativeElement.close();this.clearFiles(); }
  back() { const p=this.selected();if(p)this.show('detail',p);else this.close(); }
  invalid(name: keyof typeof this.form.controls) { const c=this.form.controls[name];return c.invalid && c.touched; }
  chooseFiles(event: Event) {
    const input=event.target as HTMLInputElement,files=Array.from(input.files || []);input.value='';
    const allowed=['image/jpeg','image/png','image/webp','image/gif','image/avif','video/mp4','video/webm','video/quicktime'];
    const total=[...this.previews().map(p=>p.file),...files];
    if(total.length>15||total.reduce((n,f)=>n+f.size,0)>50*1024*1024){this.formError.set('Choose up to 15 files, totaling no more than 50 MB.');return;}
    if(files.some(f=>!allowed.includes(f.type))){this.formError.set('Use JPG, PNG, WebP, GIF, AVIF, MP4, WebM or MOV files.');return;}
    this.previews.update(old=>[...old,...files.map(file=>({file,url:URL.createObjectURL(file)}))]);this.formError.set('');
  }
  removeFile(index: number){const previews=this.previews();URL.revokeObjectURL(previews[index].url);this.previews.set(previews.filter((_,i)=>i!==index));}
  clearFiles(){this.previews().forEach(p=>URL.revokeObjectURL(p.url));this.previews.set([]);}
  async submit(){
    this.form.markAllAsTouched();if(this.form.invalid){this.formError.set('Please check the highlighted fields.');return;}
    const panel=this.panel(),p=this.selected(),v=this.form.getRawValue();
    if((panel==='report'||panel==='after')&&!this.previews().length){this.formError.set('Add at least one photo or video.');return;}
    this.busy.set(true);this.formError.set('');
    try {
      const data=new FormData();this.previews().forEach(x=>data.append('media',x.file));
      if(panel==='report'){
        for(const name of ['title','location','city','country','description'] as const)data.append(name,v[name]);
        await this.api.create(data);try{localStorage.removeItem('cg.report-draft');}catch{}
        this.mode.set('all');this.clearFilters();
      }else if(p&&panel==='edit') await this.api.edit(p.id,{title:v.title,location:v.location,city:v.city,country:v.country,description:v.description});
      else if(p&&panel==='cleanup')await this.api.organize(p.id,{organizerName:v.organizerName,date:v.date,time:v.time,timeZone:v.timeZone,target:Number(v.target),supplies:v.supplies});
      else if(p&&panel==='after'){data.append('result',v.result);await this.api.result(p.id,data);}
      else if(p&&panel==='live')await this.api.live(p.id,true,v.url,v.consent);
      else throw Error('Choose a place and try again.');
      const refreshed=await this.refresh();this.busy.set(false);this.close();
      this.toast(refreshed?'Saved. Your community can now see the update.':'Saved successfully. Refresh the feed to see the update.');
    }catch(e){this.formError.set(this.api.error(e));}finally{this.busy.set(false);}
  }
  async react(p: Place, action: 'like' | 'join' | 'stopLive'){
    if(this.busyId())return;this.busyId.set(p.id);
    try{
      if(action==='like')await this.api.like(p.id,!p.liked);
      if(action==='join')await this.api.join(p.id,!p.joined);
      if(action==='stopLive')await this.api.live(p.id,false);
      await this.refresh();
      if(action==='join')this.toast(p.joined?'Participation cancelled.':'You joined! Find this cleanup in My activity.');
      if(action==='stopLive'){this.toast('Live status ended. Stop the video on its platform too.');const updated=this.selected();if(updated)this.show('detail',updated);}
    }catch(e){this.toast(this.api.error(e));}finally{this.busyId.set('');}
  }
  toast(text:string){this.notice.set(text);clearTimeout(this.toastTimer);this.toastTimer=setTimeout(()=>this.notice.set(''),6000);}
  ngOnDestroy(){this.clearFiles();clearTimeout(this.toastTimer);}
}
