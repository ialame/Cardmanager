package com.pcagrade.retriever.card.pokemon.source.mypcards;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PokemonExtensionParser {

    // Classe interne pour représenter une extension avec tous les champs demandés
    public static class Extension {
        private final String dataKey;
        private final String href;
        private final String title;
        private final String nome;
        private final String subtitulo;
        private final String sigla;
        private final String date;

        public Extension(String dataKey, String href, String title, String nome, String subtitulo, String sigla, String date) {
            this.dataKey = dataKey;
            this.href = href;
            this.title = title;
            this.nome = nome;
            this.subtitulo = subtitulo;
            this.sigla = sigla;
            this.date = date;
        }

        public String getDataKey() {
            return dataKey;
        }

        public String getHref() {
            return href;
        }

        public String getTitle() {
            return title;
        }

        public String getNome() {
            return nome;
        }

        public String getSubtitulo() {
            return subtitulo;
        }

        public String getSigla() {
            return sigla;
        }

        public String getDate() {
            return date;
        }

        @Override
        public String toString() {
            return "Extension{" +
                    "dataKey='" + dataKey + '\'' +
                    ", href='" + href + '\'' +
                    ", title='" + title + '\'' +
                    ", nome='" + nome + '\'' +
                    ", subtitulo='" + subtitulo + '\'' +
                    ", sigla='" + sigla + '\'' +
                    ", date='" + date + '\'' +
                    '}';
        }
    }

    // Méthode pour extraire les extensions depuis un fichier HTML local
    public static List<Extension> getPokemonExtensionsFromFile(String filePath) throws IOException {
        // Charger le fichier HTML
        File input = new File(filePath);
        Document doc = Jsoup.parse(input, "UTF-8");

        // Sélection des éléments correspondant aux extensions
        Elements extensionElements = doc.select("div.edicao-card");

        // Liste pour stocker les résultats
        List<Extension> extensions = new ArrayList<>();

        // Parcourir chaque élément d'extension
        for (Element card : extensionElements) {
            // Extraire data-key
            String dataKey = card.attr("data-key");

            // Extraire les informations de la balise <a class="edicao-link">
            Element link = card.selectFirst("a.edicao-link");
            String href = link != null ? link.attr("href") : "";
            String title = link != null ? link.attr("title") : "";

            // Extraire le nom (edicao-nome)
            String nome = card.selectFirst("h3.edicao-nome") != null
                    ? card.selectFirst("h3.edicao-nome").text()
                    : "";

            // Extraire le sous-titre (edicao-subtitulo ou placeholder)
            String subtitulo = card.selectFirst("h4.edicao-subtitulo") != null
                    ? card.selectFirst("h4.edicao-subtitulo").text()
                    : card.selectFirst("div.edicao-subtitulo-placeholder") != null
                    ? ""
                    : "";

            // Extraire la sigla (edicao-sigla)
            String sigla = card.selectFirst("div.edicao-sigla span") != null
                    ? card.selectFirst("div.edicao-sigla span").text()
                    : "";

            // Extraire la date (edicao-data)
            String date = card.selectFirst("div.edicao-data span") != null
                    ? card.selectFirst("div.edicao-data span").text()
                    : "";

            // Vérifier que les champs principaux ne sont pas vides
            if (!dataKey.isEmpty() && !href.isEmpty() && !title.isEmpty() && !nome.isEmpty() && !sigla.isEmpty()) {
                extensions.add(new Extension(dataKey, href, title, nome, subtitulo, sigla, date));
            }
        }

        return extensions;
    }

    // Méthode alternative qui retourne une Map avec dataKey comme clé
    public static Map<String, Extension> getPokemonExtensionsMapFromFile(String filePath) throws IOException {
        List<Extension> extensions = getPokemonExtensionsFromFile(filePath);
        Map<String, Extension> extensionMap = new HashMap<>();
        for (Extension ext : extensions) {
            extensionMap.put(ext.getDataKey(), ext);
        }
        return extensionMap;
    }

    // Exemple d'utilisation
    public static void main(String[] args) {
        try {
            // Chemin vers le fichier HTML local
            String filePath = "mypcards.html"; // Remplacez par le chemin réel de votre fichier

            // Récupérer la liste des extensions
            List<Extension> extensions = getPokemonExtensionsFromFile(filePath);

            // Afficher les résultats
            for (Extension ext : extensions) {
                System.out.println("Extension:");
                System.out.println("  DataKey: " + ext.getDataKey());
                System.out.println("  Href: " + ext.getHref());
                System.out.println("  Title: " + ext.getTitle());
                System.out.println("  Nome: " + ext.getNome());
                System.out.println("  Subtitulo: " + ext.getSubtitulo());
                System.out.println("  Sigla: " + ext.getSigla());
                System.out.println("  Date: " + ext.getDate());
                System.out.println();
            }

            // Exemple avec Map
            Map<String, Extension> extensionMap = getPokemonExtensionsMapFromFile(filePath);
            extensionMap.forEach((key, ext) ->
                    System.out.println("Map - DataKey: " + key + ", Extension: " + ext));

        } catch (IOException e) {
            System.err.println("Erreur lors de la récupération des données : " + e.getMessage());
        }
    }
}