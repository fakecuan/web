// Penyimpanan pesanan D.VA SKATE (Firebase Firestore, project winnerpadel).
// Data pelanggan dienkripsi di browser pelanggan dengan kunci publik admin,
// sehingga hanya halaman admin yang tahu password yang bisa membacanya.
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-app.js";
import { getAuth, signInAnonymously } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-auth.js";
import { getFirestore, collection, addDoc, getDocs, doc, setDoc, updateDoc, deleteDoc, serverTimestamp, writeBatch, Timestamp } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-firestore.js";

const FIREBASE={apiKey:'AIzaSyDiVYwwj73-pnQUii6EUc7WeZnJ-3D7CD8',authDomain:'winnerpadel.firebaseapp.com',projectId:'winnerpadel',storageBucket:'winnerpadel.firebasestorage.app',messagingSenderId:'539208934533',appId:'1:539208934533:web:919f119da78f9825a5b5ec'};
const COLLECTION='dvaOrders';
const PUBLIC_KEY='MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApy6YGmNlp0gkX4Ly+dpmyUiHS3UIcJRR5XCub3k2ngThFGKqnBr3i0PGsDmtu0U91195EZNdE1+xQb+FDAKaBfJDLV+Kup2YmZqiIZvDX7viq12JB9omMZWs1kUNpTbqftCAF9SRQS7P2oatf7o0eXyavwDveF0Y9wFGL1/TxIvQvwGMvsSThxNJ4oXa3+XLtFd0Roig14ECnNkSgC3UWqJryVA0vRMVvMusW5elB6n7rgDeWb52xMtAXQSez0nd5QRLstz/58SteMDQOajegnLSiYK2eibOdn4cbZb6+3g8jNX/8UZsD1dwDjuWWxPpT53628DEv/eqWxKKqvWPwwIDAQAB';
// Kunci privat admin, terkunci dengan password admin (PBKDF2 + AES-GCM).
const LOCKED_KEY={salt:'l4GOd4CI6N5LpCAm3fxMDg==',iv:'PszAKjdK4Bzc9Fd+',it:310000,ct:'+8Z7oX+G0jJ8qZwFV9nk+lGN1fqUhDy3zBznIYerCULX0JwgpSFkClJMzXi3Lfy/evThhl0vYkgHyWnD+UOE/SmkUUwjW1nlJ7o0Wzv1HnCb69KEyxyjopPhSKFl98kCVC8qdCydexmV10NB670cX5ei3HhPBk+i+fZIeJ/Kf6+BEdbO6MO3HfZnuZStejiikckJ+0Uej8j8cR1BqYF4ZM8hOiPRcg2hlbyhg5zTIBSjwMh6QMajLPhSt2gj+n3WcP1TBEPSmd/I9zMLi2JLMoy+9eOG/05fs/lAmrDjfYkyj1NXiAa8u6PGdyfAZzDk2aiwfjyH773MeIe8uHZ+3377LASK5eTc9tRqFGnTDM3P8Ux6ff7P/G44gzawsiXZWsl/ZRvk3uXcmagyNVhCwKyhADpMT87sD65TSH20vD04f4EjuExM2QLAOFhmLyRLfgYlQuXy/e8/akaw75m7IuiLZBEyH5JCZPC4GS1imxJGtIe8RRe5dYNuT1mhweEqeBJo23I86pPT16YW9FEUJaOTpSJlCu4ExF5kwojLo6/4Q1CPs34n4QLuPffxZbllKmRLTX5pawRizabuTEN6BE2ch5KGTVpuGbAX/TCHI5k1euX8uccFyJGIoi/2ouRQl1oWqjOrOl8zSx7ZHAQfSt0+kAcuxayXqhEGsjkhZTVtFbPkvFAQVuTN8sLsVxryC8U3+7S2K6Cp+L6EqOSg98hmW7illKPOcRy/VopLfmBsopNvdPuR0AX/RcE4oX5VCOeAYajvBujjXonWKfQS56Av4vrWVOwiPFTfA2zIW14VXAlhO7yKGQegA4j73hjQ/FX0cjLNeH8ZZwwfAvpGWjky/RXMqZ51sdwdxczkLbZpBHvZWz8gALyQOayA8DaCWc9Ee3CR9NswmUQ4hDBTuynNuQgMQfH1K/PeJxT2SyrhLCdn9xZfYsuhJrkQDcAIfEM9FqNbx5vhfokVrwsOL4S/Wv7Y1xAMzkHSdroMr+VOuUhobWJxGp86hoB+AY7It5NmjVv6YTldgDTYkrQoqxVBRljnxmGBAA+tapEQMkVjHZsU1qpfFODFPa3sAwWXRKGv9+S9Jify876wM5A8M/+40UiOOvkNm5w+TbZ10xffOTw+zF3rFd0Ul7iMT4qhu7AKqo6lTDQx6Wbi39xbB/4ThPAc6u1suFi9i4/w+8ctpWbq+Fq33ug5tOgJEJ6GOhhYfFxZIL8kxdYxcU5GMC9kZc/zRevYg9VvYNwT5PrmdnfeIXWx0xU2/S/EwDPtm6oS2za5TDaNAuc530lk9Tv95gIzuI+pWY+/wAj+k3fFIMRHpyErc3ZJYufGIfuMBDm/Z52jczDQ8RCo7c905Bin5OewZ2oEeWJbZMqM9Nv2GBNFXuNvszcHkxILLL3d4xJGcd/v8ToChKanlFedAueK0ql3Yc8LnAtR12H/NWI6/wLV0hcqe54O3M5BnLYx1+dX6ohkCGJqsGmjmNLTpBVcXYjXe8Gn06QFMdfcHJjaYKBZTT6H6ikbiQUaI/DldBiMl2T0wi2bq2eomdFIBVRQ1PDt5QXpfnKeNlgwxKJZFIIYl22XkLeATinZt4fu2xR9NBcoOMtpnsdybD78bVVHJLICPQ6Y/+HDeyX/XW0a'};

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

export async function submitOrder(data){
  await ready();
  const sealed=await seal(data);
  await addDoc(collection(db,COLLECTION),{v:1,status:'Pending',createdAt:serverTimestamp(),...sealed});
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
        out.push({...data,id:d.id,status:r.status||'Pending',created_at:r.createdAt?.toDate?.()||new Date()});
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
    async remove(id){await ready();await deleteDoc(doc(db,COLLECTION,id))}
  };
}
