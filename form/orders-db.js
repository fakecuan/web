// Penyimpanan pesanan D.VA SKATE (Firebase Firestore, project winnerpadel).
// Data pelanggan dienkripsi di browser pelanggan dengan kunci publik admin,
// sehingga hanya halaman admin yang tahu password yang bisa membacanya.
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-app.js";
import { getAuth, signInAnonymously } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-auth.js";
import { getFirestore, collection, addDoc, getDocs, doc, getDoc, setDoc, updateDoc, deleteDoc, deleteField, serverTimestamp, writeBatch, Timestamp } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-firestore.js";

const FIREBASE={apiKey:'AIzaSyDiVYwwj73-pnQUii6EUc7WeZnJ-3D7CD8',authDomain:'winnerpadel.firebaseapp.com',projectId:'winnerpadel',storageBucket:'winnerpadel.firebasestorage.app',messagingSenderId:'539208934533',appId:'1:539208934533:web:919f119da78f9825a5b5ec'};
const COLLECTION='dvaOrders';
const IMAGES='dvaOrderImages';
const PUBLIC_KEY='MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApy6YGmNlp0gkX4Ly+dpmyUiHS3UIcJRR5XCub3k2ngThFGKqnBr3i0PGsDmtu0U91195EZNdE1+xQb+FDAKaBfJDLV+Kup2YmZqiIZvDX7viq12JB9omMZWs1kUNpTbqftCAF9SRQS7P2oatf7o0eXyavwDveF0Y9wFGL1/TxIvQvwGMvsSThxNJ4oXa3+XLtFd0Roig14ECnNkSgC3UWqJryVA0vRMVvMusW5elB6n7rgDeWb52xMtAXQSez0nd5QRLstz/58SteMDQOajegnLSiYK2eibOdn4cbZb6+3g8jNX/8UZsD1dwDjuWWxPpT53628DEv/eqWxKKqvWPwwIDAQAB';
// Kunci privat admin, terkunci dengan password admin (PBKDF2 + AES-GCM).
const LOCKED_KEY={salt:'zW6CidfBv2dw/1bpY0J7Vw==',iv:'oLNGl1h3/RVrkayr',it:310000,ct:'JoPcSvFB+caV/JaDKbwjiPfnR8eBK++VMAJGgEvT/a1k8MJW3fM6n//qjiA+XlVXCG87stRdXYOQzqOmyKWQq/EyQWrJvDhaey+9ftTTz+eyIbvtR79wJti9Yb0VtxSNhpAON5kCr3gT3MpjaYDnVlibpw87xRUrJV8GcLDzNXLKAq3K0ns+vL+cpRH56Bz7DXsrEc5symCoGTT71gtu95Ydbk4OliogwhztRuVgT/AOIjcenOqi2Im6twKDUWsBajv7Pgw+0FZvEUl/MeIbFHoUtjWRQmC69VQJsSIzt/TdQP0hLCC4Dy1sO5nwvE3/JAq0eh2zKP1ZsQReE/tpAq6PTNZDIFwbpJdMSYxpW2u2eLvxdeID0PyCYHB6COX0FEkDGM080Cxnbtcp307UpJc0sVVC9VmSXN4nFhVAoEDPozf6ZUdUvxvceotWDBZu2jCjZSrS0M1EZ4hDKAPXLvT9R+txwQ8/HJ5y2XpWtp+guZIitV6UMAHJTRANRIg8ODbAGns7XbRcBVd0/hOUlygZw6or+2G05BKtw/1idGQwJPx/xbl9wAMseIm2SpOvi3/P2+Wvi1ap7GjI5eZ+hLBKxxH1SV1zF0cONoycb6ZH8bIq/PaCRdTj7WB+dCa/pdxyR5dCTHoQAXGQLNs5SBGvtte/Xt3eslK77RzHgYqSmd0ldVCCquHmaVLkrcgWlPf1WpRT4uLIGCIF8S/K9vEl/XOWhMwY17JG3A+yEz3O0q8A7FMl8Rh/ThQj/jYhIEJ4LjN/fds5Evn1ZpRWAKdEqt5k59aoPMB/0H34MlqWwwVy+V6/i0zD3eAhF3GntJhRGjSTWtEdc8w57lsPCNn7n8bh3KSa0mE6cMHtZbDQ6wCABkKGLj4ROeY1A/88jghxF3cJ+6bePKcnu0tXs93ZULQErIXktL1j9ZpubKZaboViRd+/ciRnpHAqxlV7lkJKT6cBrBSNhU0kMdMy3ZK/I2oac6Yoq3sQj7ueu779EUNDXAFxx7rfPzQ8+wwHppksgeuUtZseKJzLK1Fr8rX3suXwSwcuwkFwKET2V3yQhk+krivGGkOo4ApW1pE0Xg68tsiQMFG0/YAx89MhmYvkpI8J0jmBkn9W+aXX6RmsjEnLRwJ25r8zwYgoYyvimDb97Z69kFlolADF7DNmUbD3hEWmXBEVv1jNsuWAe68ySGlAJ/jOCrPSuPFeFsmxJLtCqwEBX90Uv49bxJsnOLs5qfFhhL0aNLZnfzPhvjzHQAuxQbDAluWLRQai0UmH0awUpXSJSRNBR+CUWWczQKIvJkEA88rng0Jii2mT5k399n3OAxNGO8Y7FMlMnyZcDOUsf6u6MkAl6OeD3cwDbsHEmkma/RYgQaecf7UrPOQJWuKKnk/ls596lxBrb0IM4AmuMBDs7DLdjbuLzPp1qvPZ8hIqLh+rTQ57bZOT5h38T+VIQ3s7nhPZibyudD1PQ9fxAHR1ruVplkvXYS8cLlm7uRlBXf4RRokBrwO295ytHh8OYhTnWn2YcmZcBrZwFTVDb6dMTwMt/Ecr9lu3VGNie7Vo2LXzrcKqgR39jD01g1Sw7uZmxm+kjEJOvg/O9Q3Q17znVhME93yvDnz+tSPPANT0jksubcfnIkZBm51h'};

const app=initializeApp(FIREBASE,'dva-orders');
const db=getFirestore(app);
let authP=null;
const ready=()=>authP||(authP=signInAnonymously(getAuth(app)).catch(e=>{authP=null;throw e}));
const enc=new TextEncoder(),dec=new TextDecoder();
const b64=buf=>btoa(String.fromCharCode(...new Uint8Array(buf)));
const unb64=s=>Uint8Array.from(atob(s),c=>c.charCodeAt(0));
let pubKeyP=null;
const pubKey=()=>pubKeyP||(pubKeyP=crypto.subtle.importKey('spki',unb64(PUBLIC_KEY),{name:'RSA-OAEP',hash:'SHA-256'},false,['encrypt']));

async function seal(data){
  const aes=await crypto.subtle.generateKey({name:'AES-GCM',length:256},true,['encrypt']);
  const iv=crypto.getRandomValues(new Uint8Array(12));
  const ct=await crypto.subtle.encrypt({name:'AES-GCM',iv},aes,enc.encode(JSON.stringify(data)));
  const ek=await crypto.subtle.encrypt({name:'RSA-OAEP'},await pubKey(),await crypto.subtle.exportKey('raw',aes));
  return {ek:b64(ek),iv:b64(iv),ct:b64(ct)};
}
async function open(sealed,priv){
  const raw=await crypto.subtle.decrypt({name:'RSA-OAEP'},priv,unb64(sealed.ek));
  const aes=await crypto.subtle.importKey('raw',raw,'AES-GCM',false,['decrypt']);
  return JSON.parse(dec.decode(await crypto.subtle.decrypt({name:'AES-GCM',iv:unb64(sealed.iv)},aes,unb64(sealed.ct))));
}

// Gambar desain: daftar {k, thumb} di dokumen pesanan; gambar penuh di dvaOrderImages/<id>_<k>.
// (Data lama: satu field `thumb` + dvaOrderImages/<id>, dibaca sebagai gambar dengan k = '').
const imgDocId=(id,k)=>k?id+'_'+k:id;
const newKey=()=>Date.now().toString(36)+Math.random().toString(36).slice(2,6);

// images (opsional): array {thumb, full} dari prepareImage().
export async function submitOrder(data,images=[]){
  await ready();
  const sealed=await seal(data);
  const list=(images||[]).map(im=>({k:newKey(),thumb:im.thumb,full:im.full}));
  const ref=await addDoc(collection(db,COLLECTION),{v:1,status:'Pending',createdAt:serverTimestamp(),...(list.length?{images:list.map(({k,thumb})=>({k,thumb}))}:{}),...sealed});
  for(const im of list) await setDoc(doc(db,IMAGES,imgDocId(ref.id,im.k)),{full:im.full});
}

// Perkecil gambar: full maks ~900 KB (Firestore), thumbnail 180 px.
export async function prepareImage(blob){
  const url=URL.createObjectURL(blob);
  try{
    const img=await new Promise((ok,no)=>{const i=new Image();i.onload=()=>ok(i);i.onerror=()=>no(new Error('File bukan gambar yang bisa dibaca'));i.src=url});
    const shrink=(max,q)=>{const k=Math.min(1,max/Math.max(img.naturalWidth,img.naturalHeight));const c=document.createElement('canvas');c.width=Math.round(img.naturalWidth*k);c.height=Math.round(img.naturalHeight*k);const x=c.getContext('2d');x.fillStyle='#fff';x.fillRect(0,0,c.width,c.height);x.drawImage(img,0,0,c.width,c.height);return c.toDataURL('image/jpeg',q)};
    let full,q=.85,max=1600;do{full=shrink(max,q);q-=.1;if(q<.5){max=Math.round(max*.75);q=.8}}while(full.length>900000);
    return {thumb:shrink(180,.75),full};
  }finally{URL.revokeObjectURL(url)}
}

// Membuka kunci admin. Melempar error bila password salah.
export async function unlock(password){
  const base=await crypto.subtle.importKey('raw',enc.encode(password),'PBKDF2',false,['deriveKey']);
  const k=await crypto.subtle.deriveKey({name:'PBKDF2',salt:unb64(LOCKED_KEY.salt),iterations:LOCKED_KEY.it,hash:'SHA-256'},base,{name:'AES-GCM',length:256},false,['decrypt']);
  let pkcs8;
  try{pkcs8=await crypto.subtle.decrypt({name:'AES-GCM',iv:unb64(LOCKED_KEY.iv)},k,unb64(LOCKED_KEY.ct))}
  catch{throw new Error('Password salah')}
  const priv=await crypto.subtle.importKey('pkcs8',pkcs8,{name:'RSA-OAEP',hash:'SHA-256'},false,['decrypt']);
  return {
    async list(){
      await ready();
      const snap=await getDocs(collection(db,COLLECTION));
      const out=[];
      for(const d of snap.docs){
        const r=d.data();let data={};
        try{data=await open(r,priv)}catch{data={customer_name:'(data tidak bisa dibuka)'}}
        out.push({...data,id:d.id,status:r.status||'Pending',images:Array.isArray(r.images)?r.images:(r.thumb?[{k:'',thumb:r.thumb}]:[]),created_at:r.createdAt?.toDate?.()||new Date()});
      }
      out.sort((a,b)=>b.created_at-a.created_at);
      out.forEach((o,i)=>o.order_number=out.length-i);
      return out;
    },
    async setStatus(id,status){await ready();await updateDoc(doc(db,COLLECTION,id),{status})},
    async save(id,status,data){await ready();await updateDoc(doc(db,COLLECTION,id),{status,...await seal(data)})},
    // Impor banyak pesanan sekaligus. id tetap (mis. sheet-12) supaya impor ulang tidak membuat duplikat.
    async importMany(list,onProgress){
      await ready();
      let done=0;
      for(let i=0;i<list.length;i+=200){
        const batch=writeBatch(db);
        for(const it of list.slice(i,i+200)){
          batch.set(doc(db,COLLECTION,it.id),{v:1,status:it.status,source:it.source||'import',createdAt:Timestamp.fromDate(it.created_at),...await seal(it.data)});
        }
        await batch.commit();done=Math.min(list.length,i+200);onProgress&&onProgress(done,list.length);
      }
    },
    // Gambar desain: thumbnail kecil di dokumen pesanan, gambar penuh di koleksi terpisah.
    // current = daftar gambar pesanan saat ini (dari list()); mengembalikan daftar baru.
    async addImage(id,current,thumb,full){await ready();const k=newKey();await setDoc(doc(db,IMAGES,imgDocId(id,k)),{full});const next=[...current,{k,thumb}];await updateDoc(doc(db,COLLECTION,id),{images:next,thumb:deleteField()});return next},
    async getImage(id,k){await ready();const s=await getDoc(doc(db,IMAGES,imgDocId(id,k)));return s.exists()?s.data().full:null},
    async removeImage(id,current,k){await ready();const next=current.filter(x=>x.k!==k);await updateDoc(doc(db,COLLECTION,id),{images:next,thumb:deleteField()});try{await deleteDoc(doc(db,IMAGES,imgDocId(id,k)))}catch{}return next},
    async remove(id,current=[]){await ready();await deleteDoc(doc(db,COLLECTION,id));for(const im of current){try{await deleteDoc(doc(db,IMAGES,imgDocId(id,im.k)))}catch{}}}
  };
}
