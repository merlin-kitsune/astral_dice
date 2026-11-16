import importlib.util, json, pathlib
spec = importlib.util.spec_from_file_location("cfu", "tools/curseforge_upload.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
class A: pass
args = A(); args.display_name = None; args.changelog_type = 'markdown'; args.manual_release = False
jar = pathlib.Path("fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar")
rel = {"projects": [{"slug": "fabric-api", "projectID": 306612, "type": "requiredDependency"}]}
job = {"jar": jar, "releaseType": "release", "gameVersions": [9638, 9639, 9990, 7499],
       "display": "Astral Dice 1.5.0-hotfix (Fabric 1.20.1)", "_changelog": None, "_relations": rel}
md = m.upload_metadata(args, job)
print("[A] 小版本分支 -> 实际进 multipart 的 metadata（json.dumps 前后一致）:")
print(json.dumps(md, ensure_ascii=False))
print('    changelog repr =', repr(md['changelog']), '| changelogType =', repr(md['changelogType']))
job2 = dict(job); job2["_changelog"] = pathlib.Path("release/1.5.0/PLAYER_CHANGELOG.md")
md2 = m.upload_metadata(args, job2)
print('[B] 主线分支 -> changelogType =', repr(md2['changelogType']), '| len(changelog) =', len(md2['changelog']))
