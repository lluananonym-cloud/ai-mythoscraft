import TopNav from "@/components/TopNav";

const Impressum = () => (
  <div className="min-h-screen flex flex-col">
    <TopNav />
    <main className="container max-w-3xl py-12 mx-auto">
      <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">Impressum</h1>
      <section className="space-y-4 text-muted-foreground">
        <p><strong>Angaben gemäß § 5 TMG</strong></p>
        <p>
          Mythos AI GmbH<br />
          Musterstraße 123<br />
          12345 Beispielstadt<br />
          Deutschland
        </p>
        <p><strong>Vertreten durch:</strong> Max Mustermann (Geschäftsführer)</p>
        <p><strong>Kontakt:</strong> E-Mail: support@mythoscraft.online – Telefon: +49 123 4567890</p>
        <p><strong>Registereintrag:</strong> Eintragung im Handelsregister.
          Registergericht: Amtsgericht Beispielstadt<br />
          Registernummer: HRB 123456
        </p>
        <p><strong>Umsatzsteuer-ID:</strong> DE 123456789</p>
        <p>Verantwortlich für den Inhalt nach § 55 Abs. 2 RStV: Max Mustermann (Adresse wie oben).</p>
      </section>
    </main>
  </div>
);

export default Impressum;
