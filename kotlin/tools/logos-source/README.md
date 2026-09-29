# Logos d'origine

Les deux fichiers de ce dossier sont les logos **tels qu'ils ont été fournis** :
un cadre 1280 × 720 transparent dans lequel le dessin occupe une petite partie.

Ils ne sont pas compilés dans l'application. Les ressources réellement utilisées
sont dans `app/src/main/res/drawable-xhdpi/` et en sont deux versions détourées :

| Source                | Ressource             | Visible          |
|-----------------------|-----------------------|------------------|
| `app_logo.png`        | `app_logo.png`        | 1003 × 240 (4,18) |
| `app_logo_noData.png` | `app_logo_no_data.png` | 792 × 510 (1,55) |

Deux écarts, tous deux imposés :

- **Le nom.** AAPT2 refuse les majuscules dans un nom de fichier de ressource
  (`'D' is not a valid file-based resource name character`) : `app_logo_noData`
  fait échouer `mergeDebugResources`. D'où `app_logo_no_data`.
- **Le détourage.** Seuls des pixels entièrement transparents ont été retirés —
  le dessin est intact. Sans cela le logo n'occuperait que 34 % de la hauteur de
  son emplacement, le reste étant du vide que la mise en page devrait compenser.

Après un nouvel export depuis l'outil de dessin, refaire les deux opérations :

```bash
python - <<'EOF'
from PIL import Image
for src, dst in (('app_logo.png', 'app_logo.png'),
                 ('app_logo_noData.png', 'app_logo_no_data.png')):
    im = Image.open(src).convert('RGBA')
    im.crop(im.split()[3].getbbox()).save('../../app/src/main/res/drawable-xhdpi/' + dst, optimize=True)
EOF
```
