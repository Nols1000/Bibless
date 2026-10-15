import { useCallback } from 'react';
import { Icon } from '../Icon.tsx';
import { useOrganiser } from '../OrganiserContext.tsx';
import { useLoad } from '../useLoad.ts';
import { PageLoad } from '../runs/PageState.tsx';
import { RunCards } from '../runs/RunCards.tsx';
import type { Organiser } from '../runs/organiser.ts';
import { DeviceFrame } from './DeviceFrame.tsx';
import iphoneQr from './screens/iphone-qr.png';
import appleWatchQr from './screens/apple-watch-qr.png';
import './home.css';

const TESTERS = 'https://groups.google.com/g/bibless-testers';
const TESTFLIGHT = 'https://testflight.apple.com/join/hd8FDfMY';
const ULTRA_MILANESE = 'watchos-ultra/Milanese Loop/AW Ultra 3 - Black + Milanese Loop.png';

function DevicePreview() {
  const base = import.meta.env.BASE_URL;
  return (
    <div className="device-preview">
      <div className="preview-orbit" aria-hidden="true" />
      <div className="phone-preview">
        <DeviceFrame device="android" src={`${base}screens/phone/barcode.png`} contentOnly
          alt="Bibless showing a runner’s QR code on a phone" />
      </div>
      <div className="watch-preview">
        <DeviceFrame device="wearos" src={`${base}screens/watch/qr.png`}
          alt="The same runner’s QR code on a Wear OS watch" />
      </div>
    </div>
  );
}

function ApplePreview() {
  return (
    <div className="apple-devices">
      <div className="preview-orbit" aria-hidden="true" />
      <figure className="apple-device apple-series-preview">
        <div className="apple-device-art"><DeviceFrame device="watchos" src={appleWatchQr}
          alt="Bibless showing a runner’s QR code on Apple Watch Series 11" /></div>
      </figure>
      <figure className="apple-device apple-phone-preview">
        <div className="apple-device-art"><DeviceFrame device="ios" src={iphoneQr}
          alt="Bibless showing a runner’s QR code on iPhone" /></div>
      </figure>
      <figure className="apple-device apple-ultra-preview">
        <div className="apple-device-art"><DeviceFrame device="watchos-ultra" variant={ULTRA_MILANESE} src={appleWatchQr}
          alt="Bibless showing a runner’s QR code on Apple Watch Ultra 3" /></div>
      </figure>
    </div>
  );
}

function SavedRuns({ organiser }: { organiser: Organiser }) {
  const load = useCallback(() => organiser.listRuns(), [organiser]);
  const { state, retry } = useLoad(load);
  if (state.status !== 'ready') {
    return <PageLoad error={state.status === 'error'} retry={retry} label="Loading your recent runs…" />;
  }
  if (state.data.length === 0) {
    return (
      <div className="first-run">
        <div className="empty-icon"><Icon name="clock" /></div>
        <div><h3>Your next run starts here.</h3><p>Create a run, bring your volunteers together and make the finish count.</p></div>
        <a className="button" href="#/new">Create your first run<Icon name="plus" /></a>
      </div>
    );
  }
  return <RunCards runs={state.data.slice(0, 3)} recent />;
}

function RecentRuns() {
  const { organiser, error, retry } = useOrganiser();
  return (
    <section className="home-recent" aria-labelledby="recent-heading">
      <div className="section-heading">
        <div><p className="eyebrow">Your starting line</p><h2 id="recent-heading">Back to your runs.</h2></div>
        <a className="text-link" href="#/runs">View all runs<Icon name="arrow" /></a>
      </div>
      {organiser ? <SavedRuns organiser={organiser} /> :
        <PageLoad error={error} retry={retry} label="Loading your recent runs…" />}
      <p className="storage-note">Saved in this browser. No account needed.</p>
    </section>
  );
}

export function HomePage() {
  return (
    <>
      <section id="top" className="home-hero" aria-labelledby="home-heading">
        <div className="hero-copy">
          <p className="eyebrow"><span className="signal-dot" aria-hidden="true" />Less to carry. More to share.</p>
          <h1 id="home-heading">Your barcode.<br />Your <span className="volt">whole run.</span></h1>
          <p className="hero-lead">From a barcode on your wrist to a finish line full of friends. Bibless helps you run light and organise free, timed community runs.</p>
          <div className="hero-actions">
            <a className="button primary" href={TESTERS}>Join the Android beta<Icon name="arrow" /></a>
            <a className="button primary" href={TESTFLIGHT}>Join the TestFlight beta<Icon name="arrow" /></a>
            <a className="button" href="#/new">Create a run<Icon name="plus" /></a>
          </div>
          <div className="privacy-points"><span><Icon name="check" />No account</span><span><Icon name="check" />No ads</span><span><Icon name="check" />No tracking</span></div>
        </div>
        <div className="hero-devices">
          <DevicePreview />
          <ApplePreview />
        </div>
      </section>

      <div className="product-grid">
        <section id="features" className="product-card wallet-card" aria-labelledby="wallet-heading">
          <div className="product-label"><span className="icon-tile"><Icon name="barcode" /></span><p className="eyebrow">For every runner</p></div>
          <h2 id="wallet-heading">Your wrist.<br />Your finish pass.</h2>
          <p>Keep your parkrun barcode on your phone and watch. No printout to remember.</p>
          <ul className="feature-points">
            <li><Icon name="barcode" /><div><strong>Ready to scan</strong><span>Barcode or QR code, at full brightness. Always black on white.</span></div></li>
            <li><Icon name="people" /><div><strong>The whole family</strong><span>Keep everyone’s barcodes together and swipe between them.</span></div></li>
            <li><Icon name="devices" /><div><strong>Phone and watch, in sync</strong><span>Add it on either device. Leave your phone at home on run day.</span></div></li>
          </ul>
          <a className="text-link" href="#beta">Get Bibless on your devices<Icon name="arrow" /></a>
        </section>
        <section id="runs" className="product-card organiser-card" aria-labelledby="organiser-heading">
          <div className="product-label"><span className="icon-tile"><Icon name="clock" /></span><p className="eyebrow">For every organiser</p></div>
          <h2 id="organiser-heading">Your community.<br />Your starting line.</h2>
          <p>A free run, a few volunteers and one place to bring it all together. Try the organiser preview.</p>
          <ol className="run-steps">
            <li><span>01</span><div><strong>Create your run</strong><p>Choose a timing mode. Print a run sheet so volunteers can join by QR code.</p></div></li>
            <li><span>02</span><div><strong>Record the finish</strong><p>Timekeepers and scanners record with the Bibless phone and watch apps.</p></div></li>
            <li><span>03</span><div><strong>Bring it together</strong><p>Import sessions, check the results and download a CSV. It all works offline.</p></div></li>
          </ol>
          <a className="text-link" href="#/new">Create a community run<Icon name="arrow" /></a>
        </section>
      </div>

      <RecentRuns />

      <section id="beta" className="beta-card" aria-labelledby="beta-heading">
        <div className="beta-copy"><p className="eyebrow">Android &amp; Wear OS beta</p><h2 id="beta-heading">See you at<br /><span className="volt">the finish.</span></h2>
          <p>Bibless is in closed testing on Google Play. Join the testers to try it on your phone and watch.</p>
          <a className="text-link" href={TESTFLIGHT}>Try the Apple beta on TestFlight<Icon name="arrow" /></a>
        </div>
        <ol className="beta-steps">
          <li><span className="step-number">1</span><div><a href={TESTERS}>Join bibless-testers</a><p>Use the Google account you use on Play.</p></div></li>
          <li><span className="step-number">2</span><div><a href="https://play.google.com/apps/testing/com.github.nols1000.bibless">Become a tester</a><p>Opt in on Google Play.</p></div></li>
          <li><span className="step-number">3</span><div><a href="https://play.google.com/store/apps/details?id=com.github.nols1000.bibless">Install Bibless</a><p>On your phone, then on your watch from its Play Store.</p></div></li>
        </ol>
      </section>
    </>
  );
}
